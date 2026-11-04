package com.rabbitc.ims.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitc.ims.meta.ModelRegistry;
import com.rabbitc.ims.model.Asset;
import com.rabbitc.ims.model.BusinessEvent;
import com.rabbitc.ims.model.Department;
import com.rabbitc.ims.model.Employee;
import com.rabbitc.ims.model.Ticket;
import com.rabbitc.ims.security.LoginUser;
import com.rabbitc.ims.service.ModelHook;
import com.rabbitc.ims.service.DataMaintenanceGate;
import com.rabbitc.ipage.enums.SortType;
import com.rabbitc.ipage.exception.CheckerException;
import com.rabbitc.ipage.jdbc.ModelSession;
import com.rabbitc.ipage.reflect.ModelMeta;
import com.rabbitc.ipage.sql.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 写入与业务操作同事务，投递在独立线程上等待 RabbitMQ 确认。 */
@Component
@ConditionalOnProperty(name = "ims.rabbitmq.enabled", havingValue = "true")
public class RabbitBusinessEvents implements ModelHook {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitBusinessEvents.class);
    private final ModelSession sql;
    private final RabbitTemplate rabbit;
    private final RabbitAdmin admin;
    private final ObjectMapper mapper;
    private final Queue queue;
    private final DataMaintenanceGate maintenance;
    private volatile boolean ready;

    public RabbitBusinessEvents(ModelSession sql, RabbitTemplate rabbit, ObjectMapper mapper,
                                DataMaintenanceGate maintenance,
                                @Value("${ims.rabbitmq.queue:ims.business.events}") String queueName) {
        this.sql = sql;
        this.maintenance = maintenance;
        this.rabbit = rabbit;
        this.admin = new RabbitAdmin(rabbit);
        this.mapper = mapper;
        this.queue = new Queue(queueName, true);
        rabbit.setMandatory(true);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ready = true;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type == Ticket.class || type == Employee.class || type == Asset.class || type == Department.class;
    }

    @Override
    public void afterCreate(ModelMeta meta, Object entity, Map<String, Object> body, LoginUser user) {
        enqueue(meta, entity, "CREATE");
    }

    @Override
    public void afterUpdate(ModelMeta meta, Object entity, Object before, Map<String, Object> body, LoginUser user) {
        enqueue(meta, entity, "UPDATE");
    }

    @Override
    public void afterDelete(ModelMeta meta, Object entity, LoginUser user) {
        enqueue(meta, entity, "DELETE");
    }

    private void enqueue(ModelMeta meta, Object entity, String action) {
        BusinessEvent event = new BusinessEvent();
        event.model = ModelRegistry.keyOf(meta.modelClass());
        event.recordId = String.valueOf(meta.idValue(entity));
        event.action = action;
        event.occurredAt = LocalDateTime.now();
        try {
            sql.insert(event);
        } catch (CheckerException e) {
            throw new IllegalStateException("无法记录业务事件", e);
        }
    }

    @Scheduled(fixedDelayString = "${ims.rabbitmq.poll-ms:5000}")
    public void publishPending() {
        var lock = maintenance.shared();
        if (!ready || !lock.tryLock()) {
            return;
        }
        try {
            List<BusinessEvent> events = sql.selectList(Query.of(BusinessEvent.class, sql.dialect())
                    .orderBy("occurredAt", SortType.ASC).orderBy("id", SortType.ASC).page(1, 50));
            if (events.isEmpty()) {
                return;
            }
            admin.declareQueue(queue);
            for (BusinessEvent event : events) {
                publish(event);
                sql.deleteById(BusinessEvent.class, event.id);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("RabbitMQ 事件暂未确认，保留待发记录：{}", e.toString());
        } finally {
            lock.unlock();
        }
    }

    private void publish(BusinessEvent event) throws Exception {
        byte[] payload = mapper.writeValueAsBytes(Map.of(
                "eventId", event.id, "model", event.model, "action", event.action,
                "recordId", event.recordId, "occurredAt", event.occurredAt.toString()));
        CorrelationData correlation = new CorrelationData(event.id);
        rabbit.send("", queue.getName(), MessageBuilder.withBody(payload)
                .setContentType("application/json").setMessageId(event.id)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build(), correlation);
        if (!correlation.getFuture().get(5, TimeUnit.SECONDS).isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("Broker 未确认事件 " + event.id);
        }
    }
}
