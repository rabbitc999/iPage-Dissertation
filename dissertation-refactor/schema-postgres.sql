--
-- PostgreSQL database dump
--

-- Dumped from database version 17.2
-- Dumped by pg_dump version 17.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: th_class; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_class (
    class_id character varying(255) NOT NULL,
    profession_id character varying(255),
    name character varying(50),
    class_type character varying(64),
    enroll_year integer,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_college; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_college (
    college_id character varying(255) NOT NULL,
    name character varying(50),
    remark character varying(200),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_direction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_direction (
    direction_id character varying(255) NOT NULL,
    name character varying(50),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_paper; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_paper (
    paper_id character varying(255) NOT NULL,
    student_id character varying(255),
    teacher_id character varying(255),
    paper_status character varying(64),
    title character varying(255),
    topic_nature character varying(255),
    topic_type character varying(255),
    topic_source character varying(255),
    topic_difficulty character varying(255),
    create_by character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_paper_score; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_paper_score (
    paper_score_id character varying(255) NOT NULL,
    paper_id character varying(255),
    defense_score numeric(18,4),
    thesis_score numeric(18,4),
    comprehensive_score numeric(18,4),
    remark character varying(255),
    create_by character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_profession; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_profession (
    profession_id character varying(255) NOT NULL,
    college_id character varying(255),
    name character varying(50),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_student; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_student (
    student_id character varying(255) NOT NULL,
    class_id character varying(255),
    student_no character varying(20),
    name character varying(30),
    remark character varying(255),
    create_by character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_teacher; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_teacher (
    teacher_id character varying(255) NOT NULL,
    teacher_no character varying(20),
    name character varying(30),
    post character varying(64),
    education character varying(64),
    qq character varying(20),
    wechat character varying(20),
    mobile character varying(20),
    remark character varying(255),
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: th_teacher_direction; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_teacher_direction (
    teacher_direction_id character varying(255) NOT NULL,
    teacher_id character varying(255),
    direction_id character varying(255)
);


--
-- Name: th_teacher_profession; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.th_teacher_profession (
    teacher_profession_id character varying(255) NOT NULL,
    teacher_id character varying(255),
    profession_id character varying(255)
);


--
-- Name: th_class th_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_class
    ADD CONSTRAINT th_class_pkey PRIMARY KEY (class_id);


--
-- Name: th_college th_college_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_college
    ADD CONSTRAINT th_college_pkey PRIMARY KEY (college_id);


--
-- Name: th_direction th_direction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_direction
    ADD CONSTRAINT th_direction_pkey PRIMARY KEY (direction_id);


--
-- Name: th_paper th_paper_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_paper
    ADD CONSTRAINT th_paper_pkey PRIMARY KEY (paper_id);


--
-- Name: th_paper_score th_paper_score_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_paper_score
    ADD CONSTRAINT th_paper_score_pkey PRIMARY KEY (paper_score_id);


--
-- Name: th_profession th_profession_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_profession
    ADD CONSTRAINT th_profession_pkey PRIMARY KEY (profession_id);


--
-- Name: th_student th_student_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_student
    ADD CONSTRAINT th_student_pkey PRIMARY KEY (student_id);


--
-- Name: th_teacher_direction th_teacher_direction_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_direction
    ADD CONSTRAINT th_teacher_direction_pkey PRIMARY KEY (teacher_direction_id);


--
-- Name: th_teacher th_teacher_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher
    ADD CONSTRAINT th_teacher_pkey PRIMARY KEY (teacher_id);


--
-- Name: th_teacher_profession th_teacher_profession_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_profession
    ADD CONSTRAINT th_teacher_profession_pkey PRIMARY KEY (teacher_profession_id);


--
-- Name: uq_th_class_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_class_name ON public.th_class USING btree (name);


--
-- Name: uq_th_college_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_college_name ON public.th_college USING btree (name);


--
-- Name: uq_th_direction_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_direction_name ON public.th_direction USING btree (name);


--
-- Name: uq_th_paper_score_paper; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_paper_score_paper ON public.th_paper_score USING btree (paper_id);


--
-- Name: uq_th_paper_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_paper_student ON public.th_paper USING btree (student_id);


--
-- Name: uq_th_profession_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_profession_name ON public.th_profession USING btree (name);


--
-- Name: uq_th_student_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_student_no ON public.th_student USING btree (student_no);


--
-- Name: uq_th_teacher_direction; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_teacher_direction ON public.th_teacher_direction USING btree (teacher_id, direction_id);


--
-- Name: uq_th_teacher_no; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_teacher_no ON public.th_teacher USING btree (teacher_no);


--
-- Name: uq_th_teacher_profession; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_th_teacher_profession ON public.th_teacher_profession USING btree (teacher_id, profession_id);


--
-- Name: th_class fk_th_class_profession; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_class
    ADD CONSTRAINT fk_th_class_profession FOREIGN KEY (profession_id) REFERENCES public.th_profession(profession_id);


--
-- Name: th_paper_score fk_th_paper_score_paper; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_paper_score
    ADD CONSTRAINT fk_th_paper_score_paper FOREIGN KEY (paper_id) REFERENCES public.th_paper(paper_id);


--
-- Name: th_paper fk_th_paper_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_paper
    ADD CONSTRAINT fk_th_paper_student FOREIGN KEY (student_id) REFERENCES public.th_student(student_id);


--
-- Name: th_paper fk_th_paper_teacher; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_paper
    ADD CONSTRAINT fk_th_paper_teacher FOREIGN KEY (teacher_id) REFERENCES public.th_teacher(teacher_id);


--
-- Name: th_profession fk_th_profession_college; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_profession
    ADD CONSTRAINT fk_th_profession_college FOREIGN KEY (college_id) REFERENCES public.th_college(college_id);


--
-- Name: th_student fk_th_student_class; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_student
    ADD CONSTRAINT fk_th_student_class FOREIGN KEY (class_id) REFERENCES public.th_class(class_id);


--
-- Name: th_teacher_direction fk_th_td_direction; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_direction
    ADD CONSTRAINT fk_th_td_direction FOREIGN KEY (direction_id) REFERENCES public.th_direction(direction_id);


--
-- Name: th_teacher_direction fk_th_td_teacher; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_direction
    ADD CONSTRAINT fk_th_td_teacher FOREIGN KEY (teacher_id) REFERENCES public.th_teacher(teacher_id);


--
-- Name: th_teacher_profession fk_th_tp_profession; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_profession
    ADD CONSTRAINT fk_th_tp_profession FOREIGN KEY (profession_id) REFERENCES public.th_profession(profession_id);


--
-- Name: th_teacher_profession fk_th_tp_teacher; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.th_teacher_profession
    ADD CONSTRAINT fk_th_tp_teacher FOREIGN KEY (teacher_id) REFERENCES public.th_teacher(teacher_id);


--
-- PostgreSQL database dump complete
--

