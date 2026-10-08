-- HLS database schema after Flyway V24 (generated with pg_dump --schema-only on PostgreSQL 16; do not edit by hand).
-- Source of truth stays backend/src/main/resources/db/migration.
--
--
-- PostgreSQL database dump
--

\restrict Ai6ENtqxx1b47yoRjvfAsTSTYNryoFBbE7ePPblzod9WagSwOfgCteReNqfgmjl

-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: attendance_mark_school_required(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.attendance_mark_school_required() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.school_id IS NULL AND NOT EXISTS (
        SELECT 1 FROM attendance_status_code c WHERE c.id = NEW.status_code_id AND c.category = 'TRAINING') THEN
        RAISE EXCEPTION 'An attendance mark needs a School unless it is a training day';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.attendance_mark_school_required() OWNER TO postgres;

--
-- Name: contract_signed_rows_are_final(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.contract_signed_rows_are_final() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% rows of a signed contract cannot be updated or deleted', TG_TABLE_NAME;
END;
$$;


ALTER FUNCTION public.contract_signed_rows_are_final() OWNER TO postgres;

--
-- Name: job_offer_no_delete(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.job_offer_no_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'A job offer is never deleted';
END;
$$;


ALTER FUNCTION public.job_offer_no_delete() OWNER TO postgres;

--
-- Name: job_offer_terms_locked(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.job_offer_terms_locked() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status <> 'DRAFT' AND NEW.status = 'DRAFT' THEN
        RAISE EXCEPTION 'The terms of an issued offer cannot be changed';
    END IF;
    IF OLD.status <> 'DRAFT' AND (
        NEW.role IS DISTINCT FROM OLD.role
        OR NEW.monthly_salary IS DISTINCT FROM OLD.monthly_salary
        OR NEW.allowances IS DISTINCT FROM OLD.allowances
        OR NEW.terms IS DISTINCT FROM OLD.terms
        OR NEW.expected_joining IS DISTINCT FROM OLD.expected_joining
        OR NEW.offer_date IS DISTINCT FROM OLD.offer_date
        OR NEW.response_deadline IS DISTINCT FROM OLD.response_deadline) THEN
        RAISE EXCEPTION 'The terms of an issued offer cannot be changed';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.job_offer_terms_locked() OWNER TO postgres;

--
-- Name: manager_designation_is_final(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.manager_designation_is_final() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'manager_designation rows are never changed or deleted';
END;
$$;


ALTER FUNCTION public.manager_designation_is_final() OWNER TO postgres;

--
-- Name: proposal_insert_only(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.proposal_insert_only() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'A proposal revision is never changed or removed';
END;
$$;


ALTER FUNCTION public.proposal_insert_only() OWNER TO postgres;

--
-- Name: stored_file_no_delete(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.stored_file_no_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'A stored file row is never deleted';
END;
$$;


ALTER FUNCTION public.stored_file_no_delete() OWNER TO postgres;

--
-- Name: stored_file_only_removal(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.stored_file_only_removal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.owner_type IS DISTINCT FROM OLD.owner_type
        OR NEW.owner_id IS DISTINCT FROM OLD.owner_id OR NEW.original_name IS DISTINCT FROM OLD.original_name
        OR NEW.content_type IS DISTINCT FROM OLD.content_type OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes
        OR NEW.sha256 IS DISTINCT FROM OLD.sha256 OR NEW.storage_path IS DISTINCT FROM OLD.storage_path
        OR NEW.added_by IS DISTINCT FROM OLD.added_by OR NEW.added_at IS DISTINCT FROM OLD.added_at
        OR (OLD.removed_at IS NOT NULL AND (NEW.removed_at IS DISTINCT FROM OLD.removed_at
            OR NEW.removed_by IS DISTINCT FROM OLD.removed_by
            OR NEW.removal_reason IS DISTINCT FROM OLD.removal_reason)) THEN
        RAISE EXCEPTION 'A stored file is never changed, only removed';
    END IF;
    RETURN NEW;
END;
$$;


ALTER FUNCTION public.stored_file_only_removal() OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activity_attachment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.activity_attachment (
    id uuid NOT NULL,
    activity_id uuid NOT NULL,
    file_id uuid NOT NULL,
    added_by uuid NOT NULL,
    added_at timestamp with time zone NOT NULL
);


ALTER TABLE public.activity_attachment OWNER TO postgres;

--
-- Name: activity_attendee; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.activity_attendee (
    activity_id uuid NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.activity_attendee OWNER TO postgres;

--
-- Name: activity_date_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.activity_date_history (
    id uuid NOT NULL,
    activity_id uuid NOT NULL,
    old_date date NOT NULL,
    new_date date NOT NULL,
    changed_by uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL
);


ALTER TABLE public.activity_date_history OWNER TO postgres;

--
-- Name: api_access_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.api_access_entry (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    source_event_id uuid NOT NULL,
    user_id uuid,
    session_id uuid,
    http_method character varying(10) NOT NULL,
    route_template character varying(200) NOT NULL,
    status_code smallint NOT NULL,
    source character varying(10) NOT NULL,
    app_version character varying(20),
    location_status character varying(20) NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    accuracy_meters real,
    location_captured_at timestamp with time zone,
    CONSTRAINT chk_api_access_location CHECK (((((location_status)::text = 'AVAILABLE'::text) AND (latitude IS NOT NULL) AND (longitude IS NOT NULL) AND (accuracy_meters IS NOT NULL) AND (location_captured_at IS NOT NULL) AND ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)) AND ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))) OR (((location_status)::text <> 'AVAILABLE'::text) AND (latitude IS NULL) AND (longitude IS NULL) AND (accuracy_meters IS NULL) AND (location_captured_at IS NULL)))),
    CONSTRAINT chk_api_access_location_status CHECK (((location_status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'PERMISSION_DENIED'::character varying, 'SERVICES_OFF'::character varying, 'NO_FIX'::character varying, 'INVALID'::character varying, 'OTHER'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
    CONSTRAINT chk_api_access_source CHECK (((source)::text = ANY ((ARRAY['WEB'::character varying, 'ANDROID'::character varying])::text[])))
);


ALTER TABLE public.api_access_entry OWNER TO postgres;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.app_user (
    id uuid NOT NULL,
    display_name character varying(255) NOT NULL,
    phone character varying(20) NOT NULL,
    password_hash character varying(255),
    active boolean DEFAULT true NOT NULL,
    linked_teacher_id uuid,
    failed_attempt_count integer DEFAULT 0 NOT NULL,
    lock_until timestamp with time zone,
    username character varying(30),
    username_lower character varying(30),
    email character varying(255)
);


ALTER TABLE public.app_user OWNER TO postgres;

--
-- Name: assessment_score; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.assessment_score (
    id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    assessment_no integer NOT NULL,
    criterion character varying(14) NOT NULL,
    score smallint NOT NULL,
    remarks character varying(300),
    assessed_by uuid NOT NULL,
    assessed_at timestamp with time zone NOT NULL,
    CONSTRAINT assessment_score_assessment_no_check CHECK ((assessment_no >= 1)),
    CONSTRAINT assessment_score_criterion_check CHECK (((criterion)::text = ANY ((ARRAY['SPEAKING'::character varying, 'ENGLISH'::character varying, 'COMMUNICATION'::character varying])::text[]))),
    CONSTRAINT assessment_score_score_check CHECK (((score >= 1) AND (score <= 5)))
);


ALTER TABLE public.assessment_score OWNER TO postgres;

--
-- Name: attendance_calendar_setting; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_calendar_setting (
    id uuid NOT NULL,
    school_id uuid,
    weekly_off_days character varying(40) NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);


ALTER TABLE public.attendance_calendar_setting OWNER TO postgres;

--
-- Name: attendance_mark; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_mark (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    mark_date date NOT NULL,
    status_code_id uuid NOT NULL,
    day_value numeric(3,2) NOT NULL,
    school_id uuid,
    note character varying(500),
    set_by_user_id uuid NOT NULL,
    set_by_kind character varying(10) NOT NULL,
    set_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    leave_request_id uuid,
    CONSTRAINT attendance_mark_day_value_check CHECK ((day_value = ANY (ARRAY[0.50, 1.00]))),
    CONSTRAINT attendance_mark_set_by_kind_check CHECK (((set_by_kind)::text = ANY ((ARRAY['SELF'::character varying, 'SUPERVISOR'::character varying])::text[])))
);


ALTER TABLE public.attendance_mark OWNER TO postgres;

--
-- Name: attendance_mark_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_mark_history (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    mark_date date NOT NULL,
    action character varying(10) NOT NULL,
    status_code_id uuid,
    day_value numeric(3,2),
    school_id uuid,
    note character varying(500),
    set_by_user_id uuid NOT NULL,
    set_by_kind character varying(10) NOT NULL,
    set_at timestamp with time zone NOT NULL,
    leave_request_id uuid,
    CONSTRAINT attendance_mark_history_action_check CHECK (((action)::text = ANY ((ARRAY['CREATED'::character varying, 'CORRECTED'::character varying, 'CLEARED'::character varying])::text[]))),
    CONSTRAINT attendance_mark_history_set_by_kind_check CHECK (((set_by_kind)::text = ANY ((ARRAY['SELF'::character varying, 'SUPERVISOR'::character varying])::text[])))
);


ALTER TABLE public.attendance_mark_history OWNER TO postgres;

--
-- Name: attendance_non_working_date; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_non_working_date (
    id uuid NOT NULL,
    on_date date NOT NULL,
    description character varying(200) NOT NULL,
    created_by uuid NOT NULL
);


ALTER TABLE public.attendance_non_working_date OWNER TO postgres;

--
-- Name: attendance_status_code; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_status_code (
    id uuid NOT NULL,
    short_code character varying(8) NOT NULL,
    name character varying(60) NOT NULL,
    category character varying(12) NOT NULL,
    weight numeric(4,2) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    system boolean DEFAULT false NOT NULL,
    sort_order integer NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT attendance_status_code_category_check CHECK (((category)::text = ANY ((ARRAY['WORKED'::character varying, 'LEAVE'::character varying, 'TRAINING'::character varying, 'NON_WORKING'::character varying])::text[]))),
    CONSTRAINT attendance_status_code_weight_check CHECK (((weight >= (0)::numeric) AND (weight <= (1)::numeric)))
);


ALTER TABLE public.attendance_status_code OWNER TO postgres;

--
-- Name: attendance_teacher_month; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_teacher_month (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    year_month character(7) NOT NULL,
    state character varying(8) NOT NULL,
    working_days numeric(6,2) NOT NULL,
    days_worked numeric(6,2) NOT NULL,
    days_leave numeric(6,2) NOT NULL,
    training_available numeric(6,2) NOT NULL,
    training_attended numeric(6,2) NOT NULL,
    unmarked integer NOT NULL,
    weighted_total numeric(7,2) NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT attendance_teacher_month_state_check CHECK (((state)::text = ANY ((ARRAY['LOCKED'::character varying, 'OPEN'::character varying])::text[]))),
    CONSTRAINT attendance_teacher_month_year_month_check CHECK ((year_month ~ '^[0-9]{4}-[0-9]{2}$'::text))
);


ALTER TABLE public.attendance_teacher_month OWNER TO postgres;

--
-- Name: attendance_teacher_month_event; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.attendance_teacher_month_event (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    year_month character(7) NOT NULL,
    event character varying(10) NOT NULL,
    reason character varying(500),
    actor_user_id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    CONSTRAINT attendance_teacher_month_event_event_check CHECK (((event)::text = ANY ((ARRAY['LOCKED'::character varying, 'REOPENED'::character varying, 'RELOCKED'::character varying])::text[])))
);


ALTER TABLE public.attendance_teacher_month_event OWNER TO postgres;

--
-- Name: campus_drive; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.campus_drive (
    id uuid NOT NULL,
    college_id uuid NOT NULL,
    season_label character varying(40),
    venue character varying(200),
    status character varying(10) NOT NULL,
    cancel_reason character varying(300),
    scheduled_by uuid NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT campus_drive_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'HELD'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT ck_campus_drive_cancel CHECK ((((status)::text <> 'CANCELLED'::text) OR (length(TRIM(BOTH FROM COALESCE(cancel_reason, ''::character varying))) > 0)))
);


ALTER TABLE public.campus_drive OWNER TO postgres;

--
-- Name: campus_drive_date; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.campus_drive_date (
    drive_id uuid NOT NULL,
    drive_date date NOT NULL
);


ALTER TABLE public.campus_drive_date OWNER TO postgres;

--
-- Name: campus_drive_interviewer; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.campus_drive_interviewer (
    drive_id uuid NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.campus_drive_interviewer OWNER TO postgres;

--
-- Name: candidate; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.candidate (
    id uuid NOT NULL,
    drive_id uuid NOT NULL,
    name character varying(160) NOT NULL,
    phone character varying(20) NOT NULL,
    phone_key character varying(10) NOT NULL,
    email character varying(200),
    degree character varying(120),
    study_year character varying(40),
    notes character varying(500),
    outcome character varying(10),
    outcome_by uuid,
    outcome_at timestamp with time zone,
    teacher_id uuid,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT candidate_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT candidate_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SELECTED'::character varying, 'WAITLISTED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT candidate_phone_key_check CHECK (((phone_key)::text ~ '^[0-9]{10}$'::text))
);


ALTER TABLE public.candidate OWNER TO postgres;

--
-- Name: candidate_outcome_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.candidate_outcome_history (
    id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    outcome character varying(10) NOT NULL,
    note character varying(300),
    changed_by uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    CONSTRAINT candidate_outcome_history_outcome_check CHECK (((outcome)::text = ANY ((ARRAY['SELECTED'::character varying, 'WAITLISTED'::character varying, 'REJECTED'::character varying])::text[])))
);


ALTER TABLE public.candidate_outcome_history OWNER TO postgres;

--
-- Name: change_history_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.change_history_entry (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    source_event_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    entity_type character varying(60) NOT NULL,
    entity_id character varying(255) NOT NULL,
    field character varying(60) NOT NULL,
    before_value text,
    after_value text
);


ALTER TABLE public.change_history_entry OWNER TO postgres;

--
-- Name: college; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.college (
    id uuid NOT NULL,
    name character varying(160) NOT NULL,
    city character varying(120) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT college_city_check CHECK ((length(TRIM(BOTH FROM city)) > 0)),
    CONSTRAINT college_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0))
);


ALTER TABLE public.college OWNER TO postgres;

--
-- Name: college_contact; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.college_contact (
    college_id uuid NOT NULL,
    role character varying(20) NOT NULL,
    name character varying(160) NOT NULL,
    phone character varying(20),
    email character varying(200),
    CONSTRAINT college_contact_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT college_contact_role_check CHECK (((role)::text = ANY ((ARRAY['PLACEMENT_OFFICER'::character varying, 'PRINCIPAL'::character varying])::text[])))
);


ALTER TABLE public.college_contact OWNER TO postgres;

--
-- Name: contract; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contract (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    state character varying(12) NOT NULL,
    salary_mode character varying(12),
    teacher_count integer,
    rate numeric(12,2),
    signed_on date,
    cycle character varying(10) DEFAULT 'MONTHLY'::character varying NOT NULL,
    starts_on date NOT NULL,
    ends_on date,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_contract_dates CHECK (((ends_on IS NULL) OR (ends_on >= starts_on))),
    CONSTRAINT ck_contract_mou CHECK ((((state)::text = 'CANCELLED'::text) OR (((state)::text = 'RATE_PENDING'::text) AND (salary_mode IS NULL) AND (teacher_count IS NULL) AND (rate IS NULL) AND (signed_on IS NULL)) OR (((state)::text = 'ACTIVE'::text) AND (salary_mode IS NOT NULL) AND (teacher_count IS NOT NULL) AND (signed_on IS NOT NULL)))),
    CONSTRAINT ck_contract_rate CHECK (((salary_mode IS NULL) OR (((salary_mode)::text = 'SAME_FOR_ALL'::text) AND (rate IS NOT NULL) AND (rate > (0)::numeric)) OR (((salary_mode)::text = 'PER_TEACHER'::text) AND (rate IS NULL)))),
    CONSTRAINT contract_cycle_check CHECK (((cycle)::text = 'MONTHLY'::text)),
    CONSTRAINT contract_salary_mode_check CHECK (((salary_mode)::text = ANY ((ARRAY['SAME_FOR_ALL'::character varying, 'PER_TEACHER'::character varying])::text[]))),
    CONSTRAINT contract_state_check CHECK (((state)::text = ANY ((ARRAY['RATE_PENDING'::character varying, 'ACTIVE'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT contract_teacher_count_check CHECK (((teacher_count >= 1) AND (teacher_count <= 500)))
);


ALTER TABLE public.contract OWNER TO postgres;

--
-- Name: contract_assignment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contract_assignment (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    school_id uuid NOT NULL,
    position_id uuid,
    starts_on date NOT NULL,
    ends_on date,
    status character varying(12) NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT contract_assignment_check CHECK (((ends_on IS NULL) OR (ends_on >= starts_on))),
    CONSTRAINT contract_assignment_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CANCELLED'::character varying, 'CORRECTED'::character varying])::text[])))
);


ALTER TABLE public.contract_assignment OWNER TO postgres;

--
-- Name: contract_position; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contract_position (
    id uuid NOT NULL,
    contract_id uuid NOT NULL,
    number integer NOT NULL,
    title character varying(80),
    salary numeric(12,2) NOT NULL,
    CONSTRAINT contract_position_number_check CHECK ((number >= 1)),
    CONSTRAINT contract_position_salary_check CHECK ((salary > (0)::numeric))
);


ALTER TABLE public.contract_position OWNER TO postgres;

--
-- Name: contract_signatory; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.contract_signatory (
    id uuid NOT NULL,
    contract_id uuid NOT NULL,
    party character varying(6) NOT NULL,
    name character varying(120) NOT NULL,
    designation character varying(120) NOT NULL,
    user_id uuid,
    CONSTRAINT contract_signatory_designation_check CHECK ((length(TRIM(BOTH FROM designation)) > 0)),
    CONSTRAINT contract_signatory_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT contract_signatory_party_check CHECK (((party)::text = ANY ((ARRAY['SCHOOL'::character varying, 'HLS'::character varying])::text[])))
);


ALTER TABLE public.contract_signatory OWNER TO postgres;

--
-- Name: designation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.designation (
    id uuid NOT NULL,
    name character varying(80) NOT NULL,
    kind character varying(8) NOT NULL,
    retired boolean DEFAULT false NOT NULL,
    held_ever boolean DEFAULT false NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT designation_kind_check CHECK (((kind)::text = ANY ((ARRAY['TEACHER'::character varying, 'MANAGER'::character varying])::text[]))),
    CONSTRAINT designation_name_check CHECK ((length(btrim((name)::text)) > 0))
);


ALTER TABLE public.designation OWNER TO postgres;

--
-- Name: employee_id_claim; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.employee_id_claim (
    employee_key character varying(20) NOT NULL,
    person_kind character varying(8) NOT NULL,
    person_id uuid NOT NULL,
    employee_id character varying(20) NOT NULL,
    person_name character varying(200) NOT NULL,
    claimed_at timestamp with time zone NOT NULL,
    CONSTRAINT employee_id_claim_person_kind_check CHECK (((person_kind)::text = ANY ((ARRAY['TEACHER'::character varying, 'MANAGER'::character varying])::text[])))
);


ALTER TABLE public.employee_id_claim OWNER TO postgres;

--
-- Name: event_publication; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.event_publication (
    id uuid NOT NULL,
    listener_id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    last_resubmission_date timestamp with time zone,
    completion_attempts integer NOT NULL,
    status character varying(255)
);


ALTER TABLE public.event_publication OWNER TO postgres;

--
-- Name: event_publication_archive; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.event_publication_archive (
    id uuid NOT NULL,
    listener_id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    serialized_event text NOT NULL,
    publication_date timestamp with time zone NOT NULL,
    completion_date timestamp with time zone,
    last_resubmission_date timestamp with time zone,
    completion_attempts integer NOT NULL,
    status character varying(255)
);


ALTER TABLE public.event_publication_archive OWNER TO postgres;

--
-- Name: induction_absence; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.induction_absence (
    id uuid NOT NULL,
    enrolment_id uuid NOT NULL,
    absent_on date NOT NULL,
    reason character varying(300) NOT NULL,
    recorded_by uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL,
    CONSTRAINT induction_absence_reason_check CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


ALTER TABLE public.induction_absence OWNER TO postgres;

--
-- Name: induction_batch; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.induction_batch (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    trainer character varying(160),
    venue_type character varying(8) NOT NULL,
    venue character varying(200),
    seat_limit integer NOT NULL,
    status character varying(10) NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_induction_batch_dates CHECK ((ends_on >= starts_on)),
    CONSTRAINT induction_batch_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT induction_batch_seat_limit_check CHECK ((seat_limit >= 1)),
    CONSTRAINT induction_batch_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT induction_batch_venue_type_check CHECK (((venue_type)::text = ANY ((ARRAY['PHYSICAL'::character varying, 'VIRTUAL'::character varying])::text[])))
);


ALTER TABLE public.induction_batch OWNER TO postgres;

--
-- Name: induction_enrolment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.induction_enrolment (
    id uuid NOT NULL,
    batch_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    enrolled_by uuid NOT NULL,
    enrolled_at timestamp with time zone NOT NULL,
    result character varying(14),
    remarks character varying(300),
    signed_by uuid,
    signed_at timestamp with time zone,
    follow_up character varying(10),
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_induction_enrolment_dates CHECK ((ends_on >= starts_on)),
    CONSTRAINT induction_enrolment_follow_up_check CHECK (((follow_up)::text = ANY ((ARRAY['NEXT_BATCH'::character varying, 'RELEASED'::character varying])::text[]))),
    CONSTRAINT induction_enrolment_result_check CHECK (((result)::text = ANY ((ARRAY['COMPLETED'::character varying, 'NOT_COMPLETED'::character varying])::text[])))
);


ALTER TABLE public.induction_enrolment OWNER TO postgres;

--
-- Name: job_offer; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.job_offer (
    id uuid NOT NULL,
    candidate_id uuid NOT NULL,
    role character varying(60) NOT NULL,
    phone_key character varying(10) NOT NULL,
    monthly_salary numeric(12,2) NOT NULL,
    allowances character varying(300),
    terms character varying(1000),
    expected_joining date,
    offer_date date NOT NULL,
    response_deadline date NOT NULL,
    status character varying(10) NOT NULL,
    supersedes_id uuid,
    decline_reason character varying(300),
    issued_by uuid,
    issued_at timestamp with time zone,
    decided_by uuid,
    decided_at timestamp with time zone,
    teacher_id uuid,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_job_offer_deadline CHECK ((response_deadline >= offer_date)),
    CONSTRAINT job_offer_monthly_salary_check CHECK ((monthly_salary > (0)::numeric)),
    CONSTRAINT job_offer_phone_key_check CHECK (((phone_key)::text ~ '^[0-9]{10}$'::text)),
    CONSTRAINT job_offer_role_check CHECK ((length(TRIM(BOTH FROM role)) > 0)),
    CONSTRAINT job_offer_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ISSUED'::character varying, 'ACCEPTED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'SUPERSEDED'::character varying])::text[])))
);


ALTER TABLE public.job_offer OWNER TO postgres;

--
-- Name: leave_request; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.leave_request (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    school_id uuid NOT NULL,
    leave_type_id uuid NOT NULL,
    first_date date NOT NULL,
    last_date date NOT NULL,
    half_day_start boolean DEFAULT false NOT NULL,
    half_day_end boolean DEFAULT false NOT NULL,
    working_days numeric(5,2) NOT NULL,
    reason character varying(500) NOT NULL,
    status character varying(12) NOT NULL,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    decision_note character varying(500),
    cancelled_by_kind character varying(12),
    created_by_user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_leave_request_dates CHECK (((last_date >= first_date) AND ((last_date - first_date) <= 89))),
    CONSTRAINT ck_leave_request_reject_reason CHECK ((((status)::text <> 'REJECTED'::text) OR (decision_note IS NOT NULL))),
    CONSTRAINT leave_request_cancelled_by_kind_check CHECK (((cancelled_by_kind)::text = ANY ((ARRAY['TEACHER'::character varying, 'SUPERVISOR'::character varying])::text[]))),
    CONSTRAINT leave_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.leave_request OWNER TO postgres;

--
-- Name: leave_type; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.leave_type (
    id uuid NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(60) NOT NULL,
    sort_order integer NOT NULL,
    active boolean DEFAULT true NOT NULL
);


ALTER TABLE public.leave_type OWNER TO postgres;

--
-- Name: login_history_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.login_history_entry (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    source_event_id uuid NOT NULL,
    user_id uuid,
    phone_masked character varying(20) NOT NULL,
    method character varying(20) NOT NULL,
    event_type character varying(30) NOT NULL,
    outcome character varying(255) NOT NULL,
    source character varying(10) DEFAULT 'WEB'::character varying NOT NULL,
    app_version character varying(20),
    location_status character varying(20) DEFAULT 'NOT_APPLICABLE'::character varying NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    accuracy_meters real,
    location_captured_at timestamp with time zone,
    device_rooted boolean DEFAULT false NOT NULL,
    CONSTRAINT chk_login_history_location CHECK (((((location_status)::text = 'AVAILABLE'::text) AND (latitude IS NOT NULL) AND (longitude IS NOT NULL) AND (accuracy_meters IS NOT NULL) AND (location_captured_at IS NOT NULL) AND ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)) AND ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))) OR (((location_status)::text <> 'AVAILABLE'::text) AND (latitude IS NULL) AND (longitude IS NULL) AND (accuracy_meters IS NULL) AND (location_captured_at IS NULL)))),
    CONSTRAINT chk_login_history_location_status CHECK (((location_status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'PERMISSION_DENIED'::character varying, 'SERVICES_OFF'::character varying, 'NO_FIX'::character varying, 'INVALID'::character varying, 'OTHER'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
    CONSTRAINT chk_login_history_source CHECK (((source)::text = ANY ((ARRAY['WEB'::character varying, 'ANDROID'::character varying])::text[])))
);


ALTER TABLE public.login_history_entry OWNER TO postgres;

--
-- Name: manager; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.manager (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    employee_id character varying(20),
    joining_date date,
    exit_date date,
    CONSTRAINT ck_manager_exit_after_joining CHECK (((exit_date IS NULL) OR (joining_date IS NULL) OR (exit_date >= joining_date)))
);


ALTER TABLE public.manager OWNER TO postgres;

--
-- Name: manager_designation; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.manager_designation (
    id uuid NOT NULL,
    seq bigint NOT NULL,
    manager_id uuid NOT NULL,
    designation_id uuid NOT NULL,
    effective_on date NOT NULL,
    recorded_by uuid NOT NULL,
    recorded_at timestamp with time zone NOT NULL
);


ALTER TABLE public.manager_designation OWNER TO postgres;

--
-- Name: manager_designation_seq_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

ALTER TABLE public.manager_designation ALTER COLUMN seq ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.manager_designation_seq_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: marketing_activity; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.marketing_activity (
    id uuid NOT NULL,
    prospect_id uuid,
    school_id uuid,
    type character varying(16) NOT NULL,
    status character varying(10) NOT NULL,
    activity_date date NOT NULL,
    notes character varying(1000),
    outcome character varying(1000),
    follow_up_on date,
    cancel_reason character varying(300),
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_marketing_activity_cancel CHECK ((((status)::text <> 'CANCELLED'::text) OR (length(TRIM(BOTH FROM COALESCE(cancel_reason, ''::character varying))) > 0))),
    CONSTRAINT ck_marketing_activity_outcome CHECK ((((status)::text <> 'COMPLETED'::text) OR (length(TRIM(BOTH FROM COALESCE(outcome, ''::character varying))) > 0))),
    CONSTRAINT ck_marketing_activity_target CHECK (((prospect_id IS NULL) <> (school_id IS NULL))),
    CONSTRAINT marketing_activity_status_check CHECK (((status)::text = ANY ((ARRAY['PLANNED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT marketing_activity_type_check CHECK (((type)::text = ANY ((ARRAY['VISIT'::character varying, 'CALL'::character varying, 'PROPOSAL_MEETING'::character varying, 'FOLLOW_UP'::character varying])::text[])))
);


ALTER TABLE public.marketing_activity OWNER TO postgres;

--
-- Name: marketing_prospect; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.marketing_prospect (
    id uuid NOT NULL,
    name character varying(200) NOT NULL,
    board character varying(60),
    address character varying(300),
    zone_id uuid NOT NULL,
    name_key character varying(320) NOT NULL,
    contact_person character varying(160),
    designation character varying(160),
    phone character varying(20),
    email character varying(200),
    expected_teachers integer,
    owner_user_id uuid NOT NULL,
    stage character varying(14) NOT NULL,
    stage_before_hold character varying(14),
    lost_reason character varying(300),
    won_at timestamp with time zone,
    won_by uuid,
    place_id uuid,
    school_id uuid,
    version bigint DEFAULT 0 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_marketing_prospect_before CHECK (((stage_before_hold IS NULL) OR ((stage_before_hold)::text = ANY ((ARRAY['PROSPECT'::character varying, 'CONTACTED'::character varying, 'VISIT'::character varying, 'FOLLOW_UP'::character varying, 'INTERESTED'::character varying, 'NEGOTIATION'::character varying, 'FINAL_STAGE'::character varying])::text[])))),
    CONSTRAINT ck_marketing_prospect_lost CHECK ((((stage)::text <> 'LOST'::text) OR (length(TRIM(BOTH FROM COALESCE(lost_reason, ''::character varying))) > 0))),
    CONSTRAINT ck_marketing_prospect_won CHECK (((won_at IS NULL) = (won_by IS NULL))),
    CONSTRAINT marketing_prospect_expected_teachers_check CHECK (((expected_teachers >= 1) AND (expected_teachers <= 500))),
    CONSTRAINT marketing_prospect_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT marketing_prospect_stage_check CHECK (((stage)::text = ANY ((ARRAY['PROSPECT'::character varying, 'CONTACTED'::character varying, 'VISIT'::character varying, 'FOLLOW_UP'::character varying, 'INTERESTED'::character varying, 'NEGOTIATION'::character varying, 'FINAL_STAGE'::character varying, 'ON_HOLD'::character varying, 'LOST'::character varying])::text[])))
);


ALTER TABLE public.marketing_prospect OWNER TO postgres;

--
-- Name: marketing_setting; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.marketing_setting (
    key character varying(60) NOT NULL,
    int_value integer,
    version bigint DEFAULT 0 NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone,
    CONSTRAINT ck_marketing_setting_overdue CHECK ((((key)::text <> 'mou_overdue_days'::text) OR ((int_value >= 1) AND (int_value <= 90))))
);


ALTER TABLE public.marketing_setting OWNER TO postgres;

--
-- Name: notification; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.notification (
    id uuid NOT NULL,
    recipient_user_id uuid NOT NULL,
    type character varying(30) NOT NULL,
    title character varying(80) NOT NULL,
    message character varying(300) NOT NULL,
    link character varying(200),
    channel character varying(10) DEFAULT 'IN_APP'::character varying NOT NULL,
    group_key character varying(120),
    detail character varying(600),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    read_at timestamp with time zone,
    CONSTRAINT ck_notification_link CHECK (((link IS NULL) OR ((link)::text ~~ '/%'::text))),
    CONSTRAINT notification_channel_check CHECK (((channel)::text = 'IN_APP'::text)),
    CONSTRAINT notification_type_check CHECK (((type)::text = ANY ((ARRAY['LEAVE_DECIDED'::character varying, 'LEAVE_REQUESTED'::character varying, 'LEAVE_CANCELLED'::character varying, 'ATTENDANCE_CHANGED'::character varying, 'ATTENDANCE_MONTH_LOCKED'::character varying, 'ATTENDANCE_MONTH_REOPENED'::character varying, 'MOU_NOT_RECORDED'::character varying])::text[])))
);


ALTER TABLE public.notification OWNER TO postgres;

--
-- Name: one_time_code; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.one_time_code (
    id uuid NOT NULL,
    channel character varying(10) NOT NULL,
    destination character varying(255) NOT NULL,
    purpose character varying(20) NOT NULL,
    code_hash character varying(255) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    wrong_attempt_count integer DEFAULT 0 NOT NULL
);


ALTER TABLE public.one_time_code OWNER TO postgres;

--
-- Name: otp_policy_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.otp_policy_settings (
    id smallint DEFAULT 1 NOT NULL,
    resend_cooldown_seconds integer DEFAULT 30 NOT NULL,
    max_consecutive_requests integer DEFAULT 5 NOT NULL,
    consecutive_request_lockout_hours integer DEFAULT 4 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT otp_policy_settings_id_check CHECK ((id = 1))
);


ALTER TABLE public.otp_policy_settings OWNER TO postgres;

--
-- Name: overdue_notice; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.overdue_notice (
    prospect_id uuid NOT NULL,
    limit_days integer NOT NULL,
    noticed_at timestamp with time zone NOT NULL
);


ALTER TABLE public.overdue_notice OWNER TO postgres;

--
-- Name: permission_matrix; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.permission_matrix (
    id uuid NOT NULL,
    role character varying(20) NOT NULL,
    module character varying(40) NOT NULL,
    action character varying(20) NOT NULL,
    granted boolean NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE public.permission_matrix OWNER TO postgres;

--
-- Name: place; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.place (
    id uuid NOT NULL,
    zone_id uuid NOT NULL,
    name character varying(160) NOT NULL,
    pin_code character(6) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT place_pin_code_check CHECK ((pin_code ~ '^[0-9]{6}$'::text))
);


ALTER TABLE public.place OWNER TO postgres;

--
-- Name: proposal_position; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.proposal_position (
    id uuid NOT NULL,
    revision_id uuid NOT NULL,
    number integer NOT NULL,
    title character varying(80),
    salary numeric(12,2) NOT NULL,
    CONSTRAINT proposal_position_number_check CHECK ((number >= 1)),
    CONSTRAINT proposal_position_salary_check CHECK ((salary > (0)::numeric))
);


ALTER TABLE public.proposal_position OWNER TO postgres;

--
-- Name: proposal_revision; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.proposal_revision (
    id uuid NOT NULL,
    prospect_id uuid NOT NULL,
    revision integer NOT NULL,
    teacher_count integer NOT NULL,
    start_month date NOT NULL,
    salary_mode character varying(12) NOT NULL,
    rate numeric(12,2),
    notes character varying(500),
    created_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT ck_proposal_rate CHECK (((((salary_mode)::text = 'SAME_FOR_ALL'::text) AND (rate IS NOT NULL) AND (rate > (0)::numeric)) OR (((salary_mode)::text = 'PER_TEACHER'::text) AND (rate IS NULL)))),
    CONSTRAINT proposal_revision_revision_check CHECK ((revision >= 1)),
    CONSTRAINT proposal_revision_salary_mode_check CHECK (((salary_mode)::text = ANY ((ARRAY['SAME_FOR_ALL'::character varying, 'PER_TEACHER'::character varying])::text[]))),
    CONSTRAINT proposal_revision_teacher_count_check CHECK (((teacher_count >= 1) AND (teacher_count <= 500)))
);


ALTER TABLE public.proposal_revision OWNER TO postgres;

--
-- Name: prospect_owner_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.prospect_owner_history (
    id uuid NOT NULL,
    prospect_id uuid NOT NULL,
    from_owner uuid,
    to_owner uuid NOT NULL,
    changed_by uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL
);


ALTER TABLE public.prospect_owner_history OWNER TO postgres;

--
-- Name: prospect_stage_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.prospect_stage_history (
    id uuid NOT NULL,
    prospect_id uuid NOT NULL,
    kind character varying(16) NOT NULL,
    from_stage character varying(14),
    to_stage character varying(14) NOT NULL,
    reason character varying(300),
    changed_by uuid NOT NULL,
    changed_at timestamp with time zone NOT NULL,
    CONSTRAINT prospect_stage_history_kind_check CHECK (((kind)::text = ANY ((ARRAY['STAGE'::character varying, 'REVIEW_APPROVED'::character varying, 'REVIEW_REJECTED'::character varying])::text[])))
);


ALTER TABLE public.prospect_stage_history OWNER TO postgres;

--
-- Name: renewal_credential; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.renewal_credential (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    credential_hash character varying(255) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    superseded_by uuid
);


ALTER TABLE public.renewal_credential OWNER TO postgres;

--
-- Name: role_assignment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.role_assignment (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(20) NOT NULL
);


ALTER TABLE public.role_assignment OWNER TO postgres;

--
-- Name: school; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.school (
    id uuid NOT NULL,
    name character varying(200) NOT NULL,
    place_id uuid NOT NULL,
    address text NOT NULL,
    contact_person character varying(120),
    contact_phone character varying(20),
    billing_contact character varying(200),
    active boolean DEFAULT true NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE public.school OWNER TO postgres;

--
-- Name: school_contact; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.school_contact (
    school_id uuid NOT NULL,
    role character varying(12) NOT NULL,
    name character varying(160) NOT NULL,
    phone character varying(20),
    email character varying(200),
    CONSTRAINT school_contact_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT school_contact_role_check CHECK (((role)::text = ANY ((ARRAY['PRINCIPAL'::character varying, 'ACCOUNTANT'::character varying])::text[])))
);


ALTER TABLE public.school_contact OWNER TO postgres;

--
-- Name: school_manager_assignment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.school_manager_assignment (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    manager_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date
);


ALTER TABLE public.school_manager_assignment OWNER TO postgres;

--
-- Name: session; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.session (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    device_description character varying(255),
    signed_in_at timestamp with time zone NOT NULL,
    last_activity_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    client_type character varying(10) DEFAULT 'WEB'::character varying NOT NULL,
    app_version character varying(20),
    CONSTRAINT chk_session_client_type CHECK (((client_type)::text = ANY ((ARRAY['WEB'::character varying, 'ANDROID'::character varying])::text[])))
);


ALTER TABLE public.session OWNER TO postgres;

--
-- Name: stored_file; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.stored_file (
    id uuid NOT NULL,
    owner_type character varying(30) NOT NULL,
    owner_id uuid NOT NULL,
    original_name character varying(255) NOT NULL,
    content_type character varying(100) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    storage_path character varying(300) NOT NULL,
    added_by uuid NOT NULL,
    added_at timestamp with time zone NOT NULL,
    removed_at timestamp with time zone,
    removed_by uuid,
    removal_reason character varying(300),
    CONSTRAINT ck_stored_file_removal CHECK ((((removed_at IS NULL) AND (removed_by IS NULL) AND (removal_reason IS NULL)) OR ((removed_at IS NOT NULL) AND (removed_by IS NOT NULL) AND (length(TRIM(BOTH FROM COALESCE(removal_reason, ''::character varying))) > 0)))),
    CONSTRAINT stored_file_size_bytes_check CHECK ((size_bytes > 0))
);


ALTER TABLE public.stored_file OWNER TO postgres;

--
-- Name: teacher; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.teacher (
    id uuid NOT NULL,
    name character varying(160) NOT NULL,
    phone character varying(20),
    email character varying(200),
    address text,
    status character varying(20) NOT NULL,
    status_effective_on date NOT NULL,
    user_id uuid,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    designation_id uuid,
    employee_id character varying(20)
);


ALTER TABLE public.teacher OWNER TO postgres;

--
-- Name: teacher_salary_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.teacher_salary_history (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    effective_on date NOT NULL,
    recorded_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT teacher_salary_history_amount_check CHECK ((amount >= (0)::numeric))
);


ALTER TABLE public.teacher_salary_history OWNER TO postgres;

--
-- Name: user_activity_entry; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_activity_entry (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    source_event_id uuid NOT NULL,
    actor_user_id uuid,
    affected_user_id uuid NOT NULL,
    action character varying(40) NOT NULL,
    detail text,
    source character varying(10) DEFAULT 'WEB'::character varying NOT NULL,
    app_version character varying(20),
    location_status character varying(20) DEFAULT 'NOT_APPLICABLE'::character varying NOT NULL,
    latitude numeric(9,6),
    longitude numeric(9,6),
    accuracy_meters real,
    location_captured_at timestamp with time zone,
    CONSTRAINT chk_user_activity_location CHECK (((((location_status)::text = 'AVAILABLE'::text) AND (latitude IS NOT NULL) AND (longitude IS NOT NULL) AND (accuracy_meters IS NOT NULL) AND (location_captured_at IS NOT NULL) AND ((latitude >= ('-90'::integer)::numeric) AND (latitude <= (90)::numeric)) AND ((longitude >= ('-180'::integer)::numeric) AND (longitude <= (180)::numeric))) OR (((location_status)::text <> 'AVAILABLE'::text) AND (latitude IS NULL) AND (longitude IS NULL) AND (accuracy_meters IS NULL) AND (location_captured_at IS NULL)))),
    CONSTRAINT chk_user_activity_location_status CHECK (((location_status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'PERMISSION_DENIED'::character varying, 'SERVICES_OFF'::character varying, 'NO_FIX'::character varying, 'INVALID'::character varying, 'OTHER'::character varying, 'NOT_APPLICABLE'::character varying])::text[]))),
    CONSTRAINT chk_user_activity_source CHECK (((source)::text = ANY ((ARRAY['WEB'::character varying, 'ANDROID'::character varying])::text[])))
);


ALTER TABLE public.user_activity_entry OWNER TO postgres;

--
-- Name: zone; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.zone (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);


ALTER TABLE public.zone OWNER TO postgres;

--
-- Name: zone_manager_assignment; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.zone_manager_assignment (
    id uuid NOT NULL,
    zone_id uuid NOT NULL,
    manager_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date
);


ALTER TABLE public.zone_manager_assignment OWNER TO postgres;

--
-- Name: activity_attachment activity_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_attachment
    ADD CONSTRAINT activity_attachment_pkey PRIMARY KEY (id);


--
-- Name: activity_attendee activity_attendee_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_attendee
    ADD CONSTRAINT activity_attendee_pkey PRIMARY KEY (activity_id, user_id);


--
-- Name: activity_date_history activity_date_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_date_history
    ADD CONSTRAINT activity_date_history_pkey PRIMARY KEY (id);


--
-- Name: api_access_entry api_access_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_access_entry
    ADD CONSTRAINT api_access_entry_pkey PRIMARY KEY (id);


--
-- Name: api_access_entry api_access_entry_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.api_access_entry
    ADD CONSTRAINT api_access_entry_source_event_id_key UNIQUE (source_event_id);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: assessment_score assessment_score_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.assessment_score
    ADD CONSTRAINT assessment_score_pkey PRIMARY KEY (id);


--
-- Name: attendance_calendar_setting attendance_calendar_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_calendar_setting
    ADD CONSTRAINT attendance_calendar_setting_pkey PRIMARY KEY (id);


--
-- Name: attendance_mark_history attendance_mark_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_mark_history
    ADD CONSTRAINT attendance_mark_history_pkey PRIMARY KEY (id);


--
-- Name: attendance_mark attendance_mark_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT attendance_mark_pkey PRIMARY KEY (id);


--
-- Name: attendance_non_working_date attendance_non_working_date_on_date_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_non_working_date
    ADD CONSTRAINT attendance_non_working_date_on_date_key UNIQUE (on_date);


--
-- Name: attendance_non_working_date attendance_non_working_date_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_non_working_date
    ADD CONSTRAINT attendance_non_working_date_pkey PRIMARY KEY (id);


--
-- Name: attendance_status_code attendance_status_code_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_status_code
    ADD CONSTRAINT attendance_status_code_pkey PRIMARY KEY (id);


--
-- Name: attendance_teacher_month_event attendance_teacher_month_event_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_teacher_month_event
    ADD CONSTRAINT attendance_teacher_month_event_pkey PRIMARY KEY (id);


--
-- Name: attendance_teacher_month attendance_teacher_month_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_teacher_month
    ADD CONSTRAINT attendance_teacher_month_pkey PRIMARY KEY (id);


--
-- Name: campus_drive_date campus_drive_date_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive_date
    ADD CONSTRAINT campus_drive_date_pkey PRIMARY KEY (drive_id, drive_date);


--
-- Name: campus_drive_interviewer campus_drive_interviewer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive_interviewer
    ADD CONSTRAINT campus_drive_interviewer_pkey PRIMARY KEY (drive_id, user_id);


--
-- Name: campus_drive campus_drive_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive
    ADD CONSTRAINT campus_drive_pkey PRIMARY KEY (id);


--
-- Name: candidate_outcome_history candidate_outcome_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate_outcome_history
    ADD CONSTRAINT candidate_outcome_history_pkey PRIMARY KEY (id);


--
-- Name: candidate candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT candidate_pkey PRIMARY KEY (id);


--
-- Name: change_history_entry change_history_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.change_history_entry
    ADD CONSTRAINT change_history_entry_pkey PRIMARY KEY (id);


--
-- Name: change_history_entry change_history_entry_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.change_history_entry
    ADD CONSTRAINT change_history_entry_source_event_id_key UNIQUE (source_event_id);


--
-- Name: college_contact college_contact_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.college_contact
    ADD CONSTRAINT college_contact_pkey PRIMARY KEY (college_id, role);


--
-- Name: college college_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.college
    ADD CONSTRAINT college_pkey PRIMARY KEY (id);


--
-- Name: contract_assignment contract_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_assignment
    ADD CONSTRAINT contract_assignment_pkey PRIMARY KEY (id);


--
-- Name: contract contract_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract
    ADD CONSTRAINT contract_pkey PRIMARY KEY (id);


--
-- Name: contract_position contract_position_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_position
    ADD CONSTRAINT contract_position_pkey PRIMARY KEY (id);


--
-- Name: contract_signatory contract_signatory_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_signatory
    ADD CONSTRAINT contract_signatory_pkey PRIMARY KEY (id);


--
-- Name: designation designation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.designation
    ADD CONSTRAINT designation_pkey PRIMARY KEY (id);


--
-- Name: employee_id_claim employee_id_claim_person_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee_id_claim
    ADD CONSTRAINT employee_id_claim_person_id_key UNIQUE (person_id);


--
-- Name: employee_id_claim employee_id_claim_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.employee_id_claim
    ADD CONSTRAINT employee_id_claim_pkey PRIMARY KEY (employee_key);


--
-- Name: event_publication_archive event_publication_archive_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.event_publication_archive
    ADD CONSTRAINT event_publication_archive_pkey PRIMARY KEY (id);


--
-- Name: event_publication event_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);


--
-- Name: contract_assignment ex_assignment_position_dates; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_assignment
    ADD CONSTRAINT ex_assignment_position_dates EXCLUDE USING gist (position_id WITH =, daterange(starts_on, COALESCE(ends_on, 'infinity'::date), '[]'::text) WITH &&) WHERE ((((status)::text = 'ACTIVE'::text) AND (position_id IS NOT NULL)));


--
-- Name: contract_assignment ex_assignment_teacher_dates; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_assignment
    ADD CONSTRAINT ex_assignment_teacher_dates EXCLUDE USING gist (teacher_id WITH =, daterange(starts_on, COALESCE(ends_on, 'infinity'::date), '[]'::text) WITH &&) WHERE (((status)::text = 'ACTIVE'::text));


--
-- Name: contract ex_contract_school_dates; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract
    ADD CONSTRAINT ex_contract_school_dates EXCLUDE USING gist (school_id WITH =, daterange(starts_on, COALESCE(ends_on, 'infinity'::date), '[]'::text) WITH &&) WHERE (((state)::text <> 'CANCELLED'::text));


--
-- Name: induction_enrolment ex_induction_enrolment_overlap; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_enrolment
    ADD CONSTRAINT ex_induction_enrolment_overlap EXCLUDE USING gist (teacher_id WITH =, daterange(starts_on, ends_on, '[]'::text) WITH &&) WHERE (((result IS NULL) OR ((result)::text = 'COMPLETED'::text)));


--
-- Name: leave_request ex_leave_request_no_overlap; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT ex_leave_request_no_overlap EXCLUDE USING gist (teacher_id WITH =, daterange(first_date, last_date, '[]'::text) WITH &&) WHERE (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying])::text[])));


--
-- Name: induction_absence induction_absence_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_absence
    ADD CONSTRAINT induction_absence_pkey PRIMARY KEY (id);


--
-- Name: induction_batch induction_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_batch
    ADD CONSTRAINT induction_batch_pkey PRIMARY KEY (id);


--
-- Name: induction_enrolment induction_enrolment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_enrolment
    ADD CONSTRAINT induction_enrolment_pkey PRIMARY KEY (id);


--
-- Name: job_offer job_offer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.job_offer
    ADD CONSTRAINT job_offer_pkey PRIMARY KEY (id);


--
-- Name: leave_request leave_request_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT leave_request_pkey PRIMARY KEY (id);


--
-- Name: leave_type leave_type_code_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_type
    ADD CONSTRAINT leave_type_code_key UNIQUE (code);


--
-- Name: leave_type leave_type_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_type
    ADD CONSTRAINT leave_type_pkey PRIMARY KEY (id);


--
-- Name: login_history_entry login_history_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.login_history_entry
    ADD CONSTRAINT login_history_entry_pkey PRIMARY KEY (id);


--
-- Name: login_history_entry login_history_entry_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.login_history_entry
    ADD CONSTRAINT login_history_entry_source_event_id_key UNIQUE (source_event_id);


--
-- Name: manager_designation manager_designation_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manager_designation
    ADD CONSTRAINT manager_designation_pkey PRIMARY KEY (id);


--
-- Name: manager manager_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manager
    ADD CONSTRAINT manager_pkey PRIMARY KEY (id);


--
-- Name: manager manager_user_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manager
    ADD CONSTRAINT manager_user_id_key UNIQUE (user_id);


--
-- Name: marketing_activity marketing_activity_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.marketing_activity
    ADD CONSTRAINT marketing_activity_pkey PRIMARY KEY (id);


--
-- Name: marketing_prospect marketing_prospect_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.marketing_prospect
    ADD CONSTRAINT marketing_prospect_pkey PRIMARY KEY (id);


--
-- Name: marketing_setting marketing_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.marketing_setting
    ADD CONSTRAINT marketing_setting_pkey PRIMARY KEY (key);


--
-- Name: notification notification_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);


--
-- Name: one_time_code one_time_code_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.one_time_code
    ADD CONSTRAINT one_time_code_pkey PRIMARY KEY (id);


--
-- Name: otp_policy_settings otp_policy_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.otp_policy_settings
    ADD CONSTRAINT otp_policy_settings_pkey PRIMARY KEY (id);


--
-- Name: overdue_notice overdue_notice_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.overdue_notice
    ADD CONSTRAINT overdue_notice_pkey PRIMARY KEY (prospect_id, limit_days);


--
-- Name: permission_matrix permission_matrix_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permission_matrix
    ADD CONSTRAINT permission_matrix_pkey PRIMARY KEY (id);


--
-- Name: place place_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.place
    ADD CONSTRAINT place_pkey PRIMARY KEY (id);


--
-- Name: proposal_position proposal_position_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_position
    ADD CONSTRAINT proposal_position_pkey PRIMARY KEY (id);


--
-- Name: proposal_revision proposal_revision_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_revision
    ADD CONSTRAINT proposal_revision_pkey PRIMARY KEY (id);


--
-- Name: prospect_owner_history prospect_owner_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prospect_owner_history
    ADD CONSTRAINT prospect_owner_history_pkey PRIMARY KEY (id);


--
-- Name: prospect_stage_history prospect_stage_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prospect_stage_history
    ADD CONSTRAINT prospect_stage_history_pkey PRIMARY KEY (id);


--
-- Name: renewal_credential renewal_credential_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_pkey PRIMARY KEY (id);


--
-- Name: role_assignment role_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_pkey PRIMARY KEY (id);


--
-- Name: school_contact school_contact_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school_contact
    ADD CONSTRAINT school_contact_pkey PRIMARY KEY (school_id, role);


--
-- Name: school_manager_assignment school_manager_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school_manager_assignment
    ADD CONSTRAINT school_manager_assignment_pkey PRIMARY KEY (id);


--
-- Name: school school_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school
    ADD CONSTRAINT school_pkey PRIMARY KEY (id);


--
-- Name: session session_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);


--
-- Name: stored_file stored_file_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.stored_file
    ADD CONSTRAINT stored_file_pkey PRIMARY KEY (id);


--
-- Name: teacher teacher_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher
    ADD CONSTRAINT teacher_pkey PRIMARY KEY (id);


--
-- Name: teacher_salary_history teacher_salary_history_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher_salary_history
    ADD CONSTRAINT teacher_salary_history_pkey PRIMARY KEY (id);


--
-- Name: teacher teacher_user_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher
    ADD CONSTRAINT teacher_user_id_key UNIQUE (user_id);


--
-- Name: app_user uq_app_user_phone; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uq_app_user_phone UNIQUE (phone);


--
-- Name: assessment_score uq_assessment_score; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.assessment_score
    ADD CONSTRAINT uq_assessment_score UNIQUE (candidate_id, assessment_no, criterion);


--
-- Name: attendance_mark uq_attendance_mark_teacher_date; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT uq_attendance_mark_teacher_date UNIQUE (teacher_id, mark_date);


--
-- Name: attendance_teacher_month uq_attendance_teacher_month; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_teacher_month
    ADD CONSTRAINT uq_attendance_teacher_month UNIQUE (teacher_id, year_month);


--
-- Name: candidate uq_candidate_drive_phone; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT uq_candidate_drive_phone UNIQUE (drive_id, phone_key);


--
-- Name: contract_position uq_contract_position_number; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_position
    ADD CONSTRAINT uq_contract_position_number UNIQUE (contract_id, number);


--
-- Name: induction_absence uq_induction_absence; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_absence
    ADD CONSTRAINT uq_induction_absence UNIQUE (enrolment_id, absent_on);


--
-- Name: induction_enrolment uq_induction_enrolment; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_enrolment
    ADD CONSTRAINT uq_induction_enrolment UNIQUE (batch_id, teacher_id);


--
-- Name: marketing_prospect uq_marketing_prospect_name_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.marketing_prospect
    ADD CONSTRAINT uq_marketing_prospect_name_key UNIQUE (name_key);


--
-- Name: permission_matrix uq_permission_matrix_role_module_action; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permission_matrix
    ADD CONSTRAINT uq_permission_matrix_role_module_action UNIQUE (role, module, action);


--
-- Name: proposal_position uq_proposal_position; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_position
    ADD CONSTRAINT uq_proposal_position UNIQUE (revision_id, number);


--
-- Name: proposal_revision uq_proposal_revision; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_revision
    ADD CONSTRAINT uq_proposal_revision UNIQUE (prospect_id, revision);


--
-- Name: role_assignment uq_role_assignment_user_role; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT uq_role_assignment_user_role UNIQUE (user_id, role);


--
-- Name: user_activity_entry user_activity_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_activity_entry
    ADD CONSTRAINT user_activity_entry_pkey PRIMARY KEY (id);


--
-- Name: user_activity_entry user_activity_entry_source_event_id_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_activity_entry
    ADD CONSTRAINT user_activity_entry_source_event_id_key UNIQUE (source_event_id);


--
-- Name: zone_manager_assignment zone_manager_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.zone_manager_assignment
    ADD CONSTRAINT zone_manager_assignment_pkey PRIMARY KEY (id);


--
-- Name: zone zone_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.zone
    ADD CONSTRAINT zone_pkey PRIMARY KEY (id);


--
-- Name: idx_activity_attachment_activity; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_activity_attachment_activity ON public.activity_attachment USING btree (activity_id);


--
-- Name: idx_activity_attachment_file; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_activity_attachment_file ON public.activity_attachment USING btree (file_id);


--
-- Name: idx_activity_attendee_user; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_activity_attendee_user ON public.activity_attendee USING btree (user_id);


--
-- Name: idx_activity_date_history_activity; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_activity_date_history_activity ON public.activity_date_history USING btree (activity_id);


--
-- Name: idx_api_access_entry_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_api_access_entry_occurred ON public.api_access_entry USING btree (occurred_at DESC);


--
-- Name: idx_api_access_entry_user_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_api_access_entry_user_occurred ON public.api_access_entry USING btree (user_id, occurred_at DESC);


--
-- Name: idx_attendance_mark_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_mark_date ON public.attendance_mark USING btree (mark_date);


--
-- Name: idx_attendance_mark_history_day; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_mark_history_day ON public.attendance_mark_history USING btree (teacher_id, mark_date, set_at);


--
-- Name: idx_attendance_mark_history_leave_request; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_mark_history_leave_request ON public.attendance_mark_history USING btree (leave_request_id) WHERE (leave_request_id IS NOT NULL);


--
-- Name: idx_attendance_mark_leave_request; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_mark_leave_request ON public.attendance_mark USING btree (leave_request_id) WHERE (leave_request_id IS NOT NULL);


--
-- Name: idx_attendance_mark_school_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_mark_school_date ON public.attendance_mark USING btree (school_id, mark_date);


--
-- Name: idx_attendance_month_event; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_attendance_month_event ON public.attendance_teacher_month_event USING btree (teacher_id, year_month, occurred_at);


--
-- Name: idx_campus_drive_college; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_campus_drive_college ON public.campus_drive USING btree (college_id);


--
-- Name: idx_campus_drive_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_campus_drive_date ON public.campus_drive_date USING btree (drive_date);


--
-- Name: idx_candidate_outcome; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_candidate_outcome ON public.candidate USING btree (outcome);


--
-- Name: idx_candidate_outcome_history_candidate; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_candidate_outcome_history_candidate ON public.candidate_outcome_history USING btree (candidate_id, changed_at);


--
-- Name: idx_candidate_phone_key; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_candidate_phone_key ON public.candidate USING btree (phone_key);


--
-- Name: idx_change_history_entry_actor_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_change_history_entry_actor_occurred ON public.change_history_entry USING btree (actor_user_id, occurred_at DESC);


--
-- Name: idx_change_history_entry_entity_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_change_history_entry_entity_occurred ON public.change_history_entry USING btree (entity_type, entity_id, occurred_at DESC);


--
-- Name: idx_change_history_entry_type_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_change_history_entry_type_occurred ON public.change_history_entry USING btree (entity_type, occurred_at DESC);


--
-- Name: idx_contract_assignment_position; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contract_assignment_position ON public.contract_assignment USING btree (position_id);


--
-- Name: idx_contract_assignment_school; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contract_assignment_school ON public.contract_assignment USING btree (school_id);


--
-- Name: idx_contract_assignment_teacher; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contract_assignment_teacher ON public.contract_assignment USING btree (teacher_id);


--
-- Name: idx_contract_school; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contract_school ON public.contract USING btree (school_id);


--
-- Name: idx_contract_signatory_contract; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_contract_signatory_contract ON public.contract_signatory USING btree (contract_id);


--
-- Name: idx_induction_enrolment_teacher; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_induction_enrolment_teacher ON public.induction_enrolment USING btree (teacher_id);


--
-- Name: idx_job_offer_candidate; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_job_offer_candidate ON public.job_offer USING btree (candidate_id);


--
-- Name: idx_job_offer_status_deadline; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_job_offer_status_deadline ON public.job_offer USING btree (status, response_deadline);


--
-- Name: idx_job_offer_teacher; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_job_offer_teacher ON public.job_offer USING btree (teacher_id) WHERE (teacher_id IS NOT NULL);


--
-- Name: idx_leave_request_status_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_leave_request_status_created ON public.leave_request USING btree (status, created_at DESC);


--
-- Name: idx_leave_request_teacher_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_leave_request_teacher_created ON public.leave_request USING btree (teacher_id, created_at DESC);


--
-- Name: idx_login_history_entry_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_history_entry_occurred ON public.login_history_entry USING btree (occurred_at DESC);


--
-- Name: idx_login_history_entry_user_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_login_history_entry_user_occurred ON public.login_history_entry USING btree (user_id, occurred_at DESC);


--
-- Name: idx_marketing_activity_date; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_activity_date ON public.marketing_activity USING btree (activity_date);


--
-- Name: idx_marketing_activity_prospect; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_activity_prospect ON public.marketing_activity USING btree (prospect_id) WHERE (prospect_id IS NOT NULL);


--
-- Name: idx_marketing_activity_school; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_activity_school ON public.marketing_activity USING btree (school_id, activity_date DESC) WHERE (school_id IS NOT NULL);


--
-- Name: idx_marketing_prospect_owner; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_prospect_owner ON public.marketing_prospect USING btree (owner_user_id);


--
-- Name: idx_marketing_prospect_school; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_prospect_school ON public.marketing_prospect USING btree (school_id) WHERE (school_id IS NOT NULL);


--
-- Name: idx_marketing_prospect_zone_stage; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_marketing_prospect_zone_stage ON public.marketing_prospect USING btree (zone_id, stage);


--
-- Name: idx_notification_created; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_created ON public.notification USING btree (created_at);


--
-- Name: idx_notification_recipient_updated; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_recipient_updated ON public.notification USING btree (recipient_user_id, updated_at DESC);


--
-- Name: idx_notification_unread_updated; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_notification_unread_updated ON public.notification USING btree (recipient_user_id, updated_at DESC) WHERE (read_at IS NULL);


--
-- Name: idx_one_time_code_destination; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_one_time_code_destination ON public.one_time_code USING btree (destination);


--
-- Name: idx_place_name_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_place_name_lower ON public.place USING btree (lower((name)::text));


--
-- Name: idx_place_pin_code; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_place_pin_code ON public.place USING btree (pin_code);


--
-- Name: idx_place_zone; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_place_zone ON public.place USING btree (zone_id);


--
-- Name: idx_prospect_owner_history_prospect; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prospect_owner_history_prospect ON public.prospect_owner_history USING btree (prospect_id, changed_at);


--
-- Name: idx_prospect_stage_history_prospect; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_prospect_stage_history_prospect ON public.prospect_stage_history USING btree (prospect_id, changed_at);


--
-- Name: idx_school_active; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_school_active ON public.school USING btree (active);


--
-- Name: idx_school_manager_manager; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_school_manager_manager ON public.school_manager_assignment USING btree (manager_id);


--
-- Name: idx_school_name_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_school_name_lower ON public.school USING btree (lower((name)::text));


--
-- Name: idx_school_place; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_school_place ON public.school USING btree (place_id);


--
-- Name: idx_session_user_id; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_session_user_id ON public.session USING btree (user_id);


--
-- Name: idx_stored_file_owner; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_stored_file_owner ON public.stored_file USING btree (owner_type, owner_id);


--
-- Name: idx_teacher_name_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_teacher_name_lower ON public.teacher USING btree (lower((name)::text));


--
-- Name: idx_teacher_salary_teacher_effective; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_teacher_salary_teacher_effective ON public.teacher_salary_history USING btree (teacher_id, effective_on DESC);


--
-- Name: idx_teacher_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_teacher_status ON public.teacher USING btree (status);


--
-- Name: idx_user_activity_entry_actor_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_activity_entry_actor_occurred ON public.user_activity_entry USING btree (actor_user_id, occurred_at DESC);


--
-- Name: idx_user_activity_entry_affected_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_activity_entry_affected_occurred ON public.user_activity_entry USING btree (affected_user_id, occurred_at DESC);


--
-- Name: idx_user_activity_entry_occurred; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_user_activity_entry_occurred ON public.user_activity_entry USING btree (occurred_at DESC);


--
-- Name: idx_zone_manager_manager; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_zone_manager_manager ON public.zone_manager_assignment USING btree (manager_id);


--
-- Name: ix_manager_designation_designation; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_manager_designation_designation ON public.manager_designation USING btree (designation_id);


--
-- Name: ix_manager_designation_lookup; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_manager_designation_lookup ON public.manager_designation USING btree (manager_id, effective_on, seq);


--
-- Name: ix_teacher_designation; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX ix_teacher_designation ON public.teacher USING btree (designation_id);


--
-- Name: uq_app_user_email; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_app_user_email ON public.app_user USING btree (email) WHERE (email IS NOT NULL);


--
-- Name: uq_app_user_username_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_app_user_username_lower ON public.app_user USING btree (username_lower) WHERE (username_lower IS NOT NULL);


--
-- Name: uq_attendance_calendar_default; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_attendance_calendar_default ON public.attendance_calendar_setting USING btree ((true)) WHERE (school_id IS NULL);


--
-- Name: uq_attendance_calendar_school; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_attendance_calendar_school ON public.attendance_calendar_setting USING btree (school_id) WHERE (school_id IS NOT NULL);


--
-- Name: uq_attendance_status_code_short; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_attendance_status_code_short ON public.attendance_status_code USING btree (lower((short_code)::text));


--
-- Name: uq_college_name_city; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_college_name_city ON public.college USING btree (lower((name)::text), lower((city)::text));


--
-- Name: uq_designation_kind_name; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_designation_kind_name ON public.designation USING btree (kind, lower(regexp_replace(btrim((name)::text), '\s+'::text, ' '::text, 'g'::text)));


--
-- Name: uq_job_offer_open; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_job_offer_open ON public.job_offer USING btree (phone_key) WHERE ((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ISSUED'::character varying])::text[]));


--
-- Name: uq_renewal_credential_hash; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_renewal_credential_hash ON public.renewal_credential USING btree (credential_hash);


--
-- Name: uq_school_manager_current; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_school_manager_current ON public.school_manager_assignment USING btree (school_id) WHERE (ends_on IS NULL);


--
-- Name: uq_zone_manager_current; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_zone_manager_current ON public.zone_manager_assignment USING btree (zone_id, manager_id) WHERE (ends_on IS NULL);


--
-- Name: uq_zone_name_lower; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_zone_name_lower ON public.zone USING btree (lower((name)::text));


--
-- Name: attendance_mark trg_attendance_mark_school_required; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_attendance_mark_school_required BEFORE INSERT OR UPDATE ON public.attendance_mark FOR EACH ROW EXECUTE FUNCTION public.attendance_mark_school_required();


--
-- Name: contract_position trg_contract_position_final; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_contract_position_final BEFORE DELETE OR UPDATE ON public.contract_position FOR EACH ROW EXECUTE FUNCTION public.contract_signed_rows_are_final();


--
-- Name: contract_signatory trg_contract_signatory_final; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_contract_signatory_final BEFORE DELETE OR UPDATE ON public.contract_signatory FOR EACH ROW EXECUTE FUNCTION public.contract_signed_rows_are_final();


--
-- Name: job_offer trg_job_offer_no_delete; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_job_offer_no_delete BEFORE DELETE ON public.job_offer FOR EACH ROW EXECUTE FUNCTION public.job_offer_no_delete();


--
-- Name: job_offer trg_job_offer_terms_locked; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_job_offer_terms_locked BEFORE UPDATE ON public.job_offer FOR EACH ROW EXECUTE FUNCTION public.job_offer_terms_locked();


--
-- Name: manager_designation trg_manager_designation_final; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_manager_designation_final BEFORE DELETE OR UPDATE ON public.manager_designation FOR EACH ROW EXECUTE FUNCTION public.manager_designation_is_final();


--
-- Name: proposal_position trg_proposal_position_insert_only; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_proposal_position_insert_only BEFORE DELETE OR UPDATE ON public.proposal_position FOR EACH ROW EXECUTE FUNCTION public.proposal_insert_only();


--
-- Name: proposal_revision trg_proposal_revision_insert_only; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_proposal_revision_insert_only BEFORE DELETE OR UPDATE ON public.proposal_revision FOR EACH ROW EXECUTE FUNCTION public.proposal_insert_only();


--
-- Name: stored_file trg_stored_file_no_delete; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_stored_file_no_delete BEFORE DELETE ON public.stored_file FOR EACH ROW EXECUTE FUNCTION public.stored_file_no_delete();


--
-- Name: stored_file trg_stored_file_only_removal; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_stored_file_only_removal BEFORE UPDATE ON public.stored_file FOR EACH ROW EXECUTE FUNCTION public.stored_file_only_removal();


--
-- Name: activity_attachment activity_attachment_activity_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_attachment
    ADD CONSTRAINT activity_attachment_activity_id_fkey FOREIGN KEY (activity_id) REFERENCES public.marketing_activity(id);


--
-- Name: activity_attachment activity_attachment_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_attachment
    ADD CONSTRAINT activity_attachment_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.stored_file(id);


--
-- Name: activity_attendee activity_attendee_activity_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_attendee
    ADD CONSTRAINT activity_attendee_activity_id_fkey FOREIGN KEY (activity_id) REFERENCES public.marketing_activity(id);


--
-- Name: activity_date_history activity_date_history_activity_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.activity_date_history
    ADD CONSTRAINT activity_date_history_activity_id_fkey FOREIGN KEY (activity_id) REFERENCES public.marketing_activity(id);


--
-- Name: assessment_score assessment_score_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.assessment_score
    ADD CONSTRAINT assessment_score_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: attendance_mark attendance_mark_status_code_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT attendance_mark_status_code_id_fkey FOREIGN KEY (status_code_id) REFERENCES public.attendance_status_code(id);


--
-- Name: campus_drive campus_drive_college_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive
    ADD CONSTRAINT campus_drive_college_id_fkey FOREIGN KEY (college_id) REFERENCES public.college(id);


--
-- Name: campus_drive_date campus_drive_date_drive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive_date
    ADD CONSTRAINT campus_drive_date_drive_id_fkey FOREIGN KEY (drive_id) REFERENCES public.campus_drive(id);


--
-- Name: campus_drive_interviewer campus_drive_interviewer_drive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.campus_drive_interviewer
    ADD CONSTRAINT campus_drive_interviewer_drive_id_fkey FOREIGN KEY (drive_id) REFERENCES public.campus_drive(id);


--
-- Name: candidate candidate_drive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate
    ADD CONSTRAINT candidate_drive_id_fkey FOREIGN KEY (drive_id) REFERENCES public.campus_drive(id);


--
-- Name: candidate_outcome_history candidate_outcome_history_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.candidate_outcome_history
    ADD CONSTRAINT candidate_outcome_history_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: college_contact college_contact_college_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.college_contact
    ADD CONSTRAINT college_contact_college_id_fkey FOREIGN KEY (college_id) REFERENCES public.college(id);


--
-- Name: contract_assignment contract_assignment_position_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_assignment
    ADD CONSTRAINT contract_assignment_position_id_fkey FOREIGN KEY (position_id) REFERENCES public.contract_position(id);


--
-- Name: contract_position contract_position_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_position
    ADD CONSTRAINT contract_position_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.contract(id);


--
-- Name: contract_signatory contract_signatory_contract_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.contract_signatory
    ADD CONSTRAINT contract_signatory_contract_id_fkey FOREIGN KEY (contract_id) REFERENCES public.contract(id);


--
-- Name: induction_absence induction_absence_enrolment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_absence
    ADD CONSTRAINT induction_absence_enrolment_id_fkey FOREIGN KEY (enrolment_id) REFERENCES public.induction_enrolment(id);


--
-- Name: induction_enrolment induction_enrolment_batch_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.induction_enrolment
    ADD CONSTRAINT induction_enrolment_batch_id_fkey FOREIGN KEY (batch_id) REFERENCES public.induction_batch(id);


--
-- Name: job_offer job_offer_candidate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.job_offer
    ADD CONSTRAINT job_offer_candidate_id_fkey FOREIGN KEY (candidate_id) REFERENCES public.candidate(id);


--
-- Name: job_offer job_offer_supersedes_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.job_offer
    ADD CONSTRAINT job_offer_supersedes_id_fkey FOREIGN KEY (supersedes_id) REFERENCES public.job_offer(id);


--
-- Name: leave_request leave_request_leave_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT leave_request_leave_type_id_fkey FOREIGN KEY (leave_type_id) REFERENCES public.leave_type(id);


--
-- Name: manager_designation manager_designation_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.manager_designation
    ADD CONSTRAINT manager_designation_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.manager(id);


--
-- Name: marketing_activity marketing_activity_prospect_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.marketing_activity
    ADD CONSTRAINT marketing_activity_prospect_id_fkey FOREIGN KEY (prospect_id) REFERENCES public.marketing_prospect(id);


--
-- Name: overdue_notice overdue_notice_prospect_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.overdue_notice
    ADD CONSTRAINT overdue_notice_prospect_id_fkey FOREIGN KEY (prospect_id) REFERENCES public.marketing_prospect(id);


--
-- Name: place place_zone_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.place
    ADD CONSTRAINT place_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zone(id);


--
-- Name: proposal_position proposal_position_revision_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_position
    ADD CONSTRAINT proposal_position_revision_id_fkey FOREIGN KEY (revision_id) REFERENCES public.proposal_revision(id);


--
-- Name: proposal_revision proposal_revision_prospect_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.proposal_revision
    ADD CONSTRAINT proposal_revision_prospect_id_fkey FOREIGN KEY (prospect_id) REFERENCES public.marketing_prospect(id);


--
-- Name: prospect_owner_history prospect_owner_history_prospect_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prospect_owner_history
    ADD CONSTRAINT prospect_owner_history_prospect_id_fkey FOREIGN KEY (prospect_id) REFERENCES public.marketing_prospect(id);


--
-- Name: prospect_stage_history prospect_stage_history_prospect_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.prospect_stage_history
    ADD CONSTRAINT prospect_stage_history_prospect_id_fkey FOREIGN KEY (prospect_id) REFERENCES public.marketing_prospect(id);


--
-- Name: renewal_credential renewal_credential_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.session(id);


--
-- Name: renewal_credential renewal_credential_superseded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_superseded_by_fkey FOREIGN KEY (superseded_by) REFERENCES public.renewal_credential(id);


--
-- Name: role_assignment role_assignment_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: school_contact school_contact_school_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school_contact
    ADD CONSTRAINT school_contact_school_id_fkey FOREIGN KEY (school_id) REFERENCES public.school(id);


--
-- Name: school_manager_assignment school_manager_assignment_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school_manager_assignment
    ADD CONSTRAINT school_manager_assignment_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.manager(id);


--
-- Name: school school_place_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.school
    ADD CONSTRAINT school_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.place(id);


--
-- Name: session session_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: teacher_salary_history teacher_salary_history_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.teacher_salary_history
    ADD CONSTRAINT teacher_salary_history_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teacher(id);


--
-- Name: zone_manager_assignment zone_manager_assignment_manager_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.zone_manager_assignment
    ADD CONSTRAINT zone_manager_assignment_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.manager(id);


--
-- PostgreSQL database dump complete
--

\unrestrict Ai6ENtqxx1b47yoRjvfAsTSTYNryoFBbE7ePPblzod9WagSwOfgCteReNqfgmjl

