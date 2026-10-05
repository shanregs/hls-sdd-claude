-- HLS database schema after Flyway V19 (generated with pg_dump --schema-only on PostgreSQL 16; do not edit by hand).
-- Source of truth stays backend/src/main/resources/db/migration.
\restrict dEAEoWfm5F6fjoZrNggZLE3NaIEQtgQYdEBdlpssTWrVfUm71JIB8v9Wd7ZUhcg
CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;
COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';
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
CREATE TABLE public.attendance_calendar_setting (
    id uuid NOT NULL,
    school_id uuid,
    weekly_off_days character varying(40) NOT NULL,
    version bigint DEFAULT 0 NOT NULL
);
CREATE TABLE public.attendance_mark (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    mark_date date NOT NULL,
    status_code_id uuid NOT NULL,
    day_value numeric(3,2) NOT NULL,
    school_id uuid NOT NULL,
    note character varying(500),
    set_by_user_id uuid NOT NULL,
    set_by_kind character varying(10) NOT NULL,
    set_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    leave_request_id uuid,
    CONSTRAINT attendance_mark_day_value_check CHECK ((day_value = ANY (ARRAY[0.50, 1.00]))),
    CONSTRAINT attendance_mark_set_by_kind_check CHECK (((set_by_kind)::text = ANY ((ARRAY['SELF'::character varying, 'SUPERVISOR'::character varying])::text[])))
);
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
CREATE TABLE public.attendance_non_working_date (
    id uuid NOT NULL,
    on_date date NOT NULL,
    description character varying(200) NOT NULL,
    created_by uuid NOT NULL
);
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
CREATE TABLE public.leave_type (
    id uuid NOT NULL,
    code character varying(20) NOT NULL,
    name character varying(60) NOT NULL,
    sort_order integer NOT NULL,
    active boolean DEFAULT true NOT NULL
);
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
CREATE TABLE public.manager (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    active boolean DEFAULT true NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
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
    CONSTRAINT notification_type_check CHECK (((type)::text = ANY ((ARRAY['LEAVE_DECIDED'::character varying, 'LEAVE_REQUESTED'::character varying, 'LEAVE_CANCELLED'::character varying, 'ATTENDANCE_CHANGED'::character varying, 'ATTENDANCE_MONTH_LOCKED'::character varying, 'ATTENDANCE_MONTH_REOPENED'::character varying])::text[])))
);
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
CREATE TABLE public.otp_policy_settings (
    id smallint DEFAULT 1 NOT NULL,
    resend_cooldown_seconds integer DEFAULT 30 NOT NULL,
    max_consecutive_requests integer DEFAULT 5 NOT NULL,
    consecutive_request_lockout_hours integer DEFAULT 4 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT otp_policy_settings_id_check CHECK ((id = 1))
);
CREATE TABLE public.permission_matrix (
    id uuid NOT NULL,
    role character varying(20) NOT NULL,
    module character varying(40) NOT NULL,
    action character varying(20) NOT NULL,
    granted boolean NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE public.place (
    id uuid NOT NULL,
    zone_id uuid NOT NULL,
    name character varying(160) NOT NULL,
    pin_code character(6) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT place_pin_code_check CHECK ((pin_code ~ '^[0-9]{6}$'::text))
);
CREATE TABLE public.renewal_credential (
    id uuid NOT NULL,
    session_id uuid NOT NULL,
    credential_hash character varying(255) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    superseded_by uuid
);
CREATE TABLE public.role_assignment (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(20) NOT NULL
);
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
CREATE TABLE public.school_manager_assignment (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    manager_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date
);
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
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE public.teacher_placement (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    school_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date,
    status character varying(12) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT teacher_placement_check CHECK (((ends_on IS NULL) OR (ends_on >= starts_on)))
);
CREATE TABLE public.teacher_salary_history (
    id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    amount numeric(12,2) NOT NULL,
    effective_on date NOT NULL,
    recorded_by uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT teacher_salary_history_amount_check CHECK ((amount >= (0)::numeric))
);
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
CREATE TABLE public.zone (
    id uuid NOT NULL,
    name character varying(120) NOT NULL,
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL
);
CREATE TABLE public.zone_manager_assignment (
    id uuid NOT NULL,
    zone_id uuid NOT NULL,
    manager_id uuid NOT NULL,
    starts_on date NOT NULL,
    ends_on date
);
ALTER TABLE ONLY public.api_access_entry
    ADD CONSTRAINT api_access_entry_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.api_access_entry
    ADD CONSTRAINT api_access_entry_source_event_id_key UNIQUE (source_event_id);
ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_calendar_setting
    ADD CONSTRAINT attendance_calendar_setting_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_mark_history
    ADD CONSTRAINT attendance_mark_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT attendance_mark_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_non_working_date
    ADD CONSTRAINT attendance_non_working_date_on_date_key UNIQUE (on_date);
ALTER TABLE ONLY public.attendance_non_working_date
    ADD CONSTRAINT attendance_non_working_date_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_status_code
    ADD CONSTRAINT attendance_status_code_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_teacher_month_event
    ADD CONSTRAINT attendance_teacher_month_event_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.attendance_teacher_month
    ADD CONSTRAINT attendance_teacher_month_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.change_history_entry
    ADD CONSTRAINT change_history_entry_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.change_history_entry
    ADD CONSTRAINT change_history_entry_source_event_id_key UNIQUE (source_event_id);
ALTER TABLE ONLY public.event_publication_archive
    ADD CONSTRAINT event_publication_archive_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.event_publication
    ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT ex_leave_request_no_overlap EXCLUDE USING gist (teacher_id WITH =, daterange(first_date, last_date, '[]'::text) WITH &&) WHERE (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying])::text[])));
ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT leave_request_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.leave_type
    ADD CONSTRAINT leave_type_code_key UNIQUE (code);
ALTER TABLE ONLY public.leave_type
    ADD CONSTRAINT leave_type_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.login_history_entry
    ADD CONSTRAINT login_history_entry_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.login_history_entry
    ADD CONSTRAINT login_history_entry_source_event_id_key UNIQUE (source_event_id);
ALTER TABLE ONLY public.manager
    ADD CONSTRAINT manager_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.manager
    ADD CONSTRAINT manager_user_id_key UNIQUE (user_id);
ALTER TABLE ONLY public.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.one_time_code
    ADD CONSTRAINT one_time_code_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.otp_policy_settings
    ADD CONSTRAINT otp_policy_settings_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.permission_matrix
    ADD CONSTRAINT permission_matrix_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.place
    ADD CONSTRAINT place_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.school_manager_assignment
    ADD CONSTRAINT school_manager_assignment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.school
    ADD CONSTRAINT school_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.teacher
    ADD CONSTRAINT teacher_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.teacher_placement
    ADD CONSTRAINT teacher_placement_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.teacher_placement
    ADD CONSTRAINT teacher_placement_teacher_id_daterange_excl EXCLUDE USING gist (teacher_id WITH =, daterange(starts_on, COALESCE(ends_on, 'infinity'::date), '[]'::text) WITH &&) WHERE (((status)::text = 'ACTIVE'::text));
ALTER TABLE ONLY public.teacher_salary_history
    ADD CONSTRAINT teacher_salary_history_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.teacher
    ADD CONSTRAINT teacher_user_id_key UNIQUE (user_id);
ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT uq_app_user_phone UNIQUE (phone);
ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT uq_attendance_mark_teacher_date UNIQUE (teacher_id, mark_date);
ALTER TABLE ONLY public.attendance_teacher_month
    ADD CONSTRAINT uq_attendance_teacher_month UNIQUE (teacher_id, year_month);
ALTER TABLE ONLY public.permission_matrix
    ADD CONSTRAINT uq_permission_matrix_role_module_action UNIQUE (role, module, action);
ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT uq_role_assignment_user_role UNIQUE (user_id, role);
ALTER TABLE ONLY public.user_activity_entry
    ADD CONSTRAINT user_activity_entry_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.user_activity_entry
    ADD CONSTRAINT user_activity_entry_source_event_id_key UNIQUE (source_event_id);
ALTER TABLE ONLY public.zone_manager_assignment
    ADD CONSTRAINT zone_manager_assignment_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.zone
    ADD CONSTRAINT zone_pkey PRIMARY KEY (id);
CREATE INDEX idx_api_access_entry_occurred ON public.api_access_entry USING btree (occurred_at DESC);
CREATE INDEX idx_api_access_entry_user_occurred ON public.api_access_entry USING btree (user_id, occurred_at DESC);
CREATE INDEX idx_attendance_mark_date ON public.attendance_mark USING btree (mark_date);
CREATE INDEX idx_attendance_mark_history_day ON public.attendance_mark_history USING btree (teacher_id, mark_date, set_at);
CREATE INDEX idx_attendance_mark_history_leave_request ON public.attendance_mark_history USING btree (leave_request_id) WHERE (leave_request_id IS NOT NULL);
CREATE INDEX idx_attendance_mark_leave_request ON public.attendance_mark USING btree (leave_request_id) WHERE (leave_request_id IS NOT NULL);
CREATE INDEX idx_attendance_mark_school_date ON public.attendance_mark USING btree (school_id, mark_date);
CREATE INDEX idx_attendance_month_event ON public.attendance_teacher_month_event USING btree (teacher_id, year_month, occurred_at);
CREATE INDEX idx_change_history_entry_actor_occurred ON public.change_history_entry USING btree (actor_user_id, occurred_at DESC);
CREATE INDEX idx_change_history_entry_entity_occurred ON public.change_history_entry USING btree (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_change_history_entry_type_occurred ON public.change_history_entry USING btree (entity_type, occurred_at DESC);
CREATE INDEX idx_leave_request_status_created ON public.leave_request USING btree (status, created_at DESC);
CREATE INDEX idx_leave_request_teacher_created ON public.leave_request USING btree (teacher_id, created_at DESC);
CREATE INDEX idx_login_history_entry_occurred ON public.login_history_entry USING btree (occurred_at DESC);
CREATE INDEX idx_login_history_entry_user_occurred ON public.login_history_entry USING btree (user_id, occurred_at DESC);
CREATE INDEX idx_notification_created ON public.notification USING btree (created_at);
CREATE INDEX idx_notification_recipient_updated ON public.notification USING btree (recipient_user_id, updated_at DESC);
CREATE INDEX idx_notification_unread_updated ON public.notification USING btree (recipient_user_id, updated_at DESC) WHERE (read_at IS NULL);
CREATE INDEX idx_one_time_code_destination ON public.one_time_code USING btree (destination);
CREATE INDEX idx_place_name_lower ON public.place USING btree (lower((name)::text));
CREATE INDEX idx_place_pin_code ON public.place USING btree (pin_code);
CREATE INDEX idx_place_zone ON public.place USING btree (zone_id);
CREATE INDEX idx_school_active ON public.school USING btree (active);
CREATE INDEX idx_school_manager_manager ON public.school_manager_assignment USING btree (manager_id);
CREATE INDEX idx_school_name_lower ON public.school USING btree (lower((name)::text));
CREATE INDEX idx_school_place ON public.school USING btree (place_id);
CREATE INDEX idx_session_user_id ON public.session USING btree (user_id);
CREATE INDEX idx_teacher_name_lower ON public.teacher USING btree (lower((name)::text));
CREATE INDEX idx_teacher_placement_school ON public.teacher_placement USING btree (school_id);
CREATE INDEX idx_teacher_placement_teacher ON public.teacher_placement USING btree (teacher_id);
CREATE INDEX idx_teacher_salary_teacher_effective ON public.teacher_salary_history USING btree (teacher_id, effective_on DESC);
CREATE INDEX idx_teacher_status ON public.teacher USING btree (status);
CREATE INDEX idx_user_activity_entry_actor_occurred ON public.user_activity_entry USING btree (actor_user_id, occurred_at DESC);
CREATE INDEX idx_user_activity_entry_affected_occurred ON public.user_activity_entry USING btree (affected_user_id, occurred_at DESC);
CREATE INDEX idx_user_activity_entry_occurred ON public.user_activity_entry USING btree (occurred_at DESC);
CREATE INDEX idx_zone_manager_manager ON public.zone_manager_assignment USING btree (manager_id);
CREATE UNIQUE INDEX uq_app_user_email ON public.app_user USING btree (email) WHERE (email IS NOT NULL);
CREATE UNIQUE INDEX uq_app_user_username_lower ON public.app_user USING btree (username_lower) WHERE (username_lower IS NOT NULL);
CREATE UNIQUE INDEX uq_attendance_calendar_default ON public.attendance_calendar_setting USING btree ((true)) WHERE (school_id IS NULL);
CREATE UNIQUE INDEX uq_attendance_calendar_school ON public.attendance_calendar_setting USING btree (school_id) WHERE (school_id IS NOT NULL);
CREATE UNIQUE INDEX uq_attendance_status_code_short ON public.attendance_status_code USING btree (lower((short_code)::text));
CREATE UNIQUE INDEX uq_renewal_credential_hash ON public.renewal_credential USING btree (credential_hash);
CREATE UNIQUE INDEX uq_school_manager_current ON public.school_manager_assignment USING btree (school_id) WHERE (ends_on IS NULL);
CREATE UNIQUE INDEX uq_zone_manager_current ON public.zone_manager_assignment USING btree (zone_id, manager_id) WHERE (ends_on IS NULL);
CREATE UNIQUE INDEX uq_zone_name_lower ON public.zone USING btree (lower((name)::text));
ALTER TABLE ONLY public.attendance_mark
    ADD CONSTRAINT attendance_mark_status_code_id_fkey FOREIGN KEY (status_code_id) REFERENCES public.attendance_status_code(id);
ALTER TABLE ONLY public.leave_request
    ADD CONSTRAINT leave_request_leave_type_id_fkey FOREIGN KEY (leave_type_id) REFERENCES public.leave_type(id);
ALTER TABLE ONLY public.place
    ADD CONSTRAINT place_zone_id_fkey FOREIGN KEY (zone_id) REFERENCES public.zone(id);
ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.session(id);
ALTER TABLE ONLY public.renewal_credential
    ADD CONSTRAINT renewal_credential_superseded_by_fkey FOREIGN KEY (superseded_by) REFERENCES public.renewal_credential(id);
ALTER TABLE ONLY public.role_assignment
    ADD CONSTRAINT role_assignment_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);
ALTER TABLE ONLY public.school_manager_assignment
    ADD CONSTRAINT school_manager_assignment_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.manager(id);
ALTER TABLE ONLY public.school
    ADD CONSTRAINT school_place_id_fkey FOREIGN KEY (place_id) REFERENCES public.place(id);
ALTER TABLE ONLY public.session
    ADD CONSTRAINT session_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id);
ALTER TABLE ONLY public.teacher_placement
    ADD CONSTRAINT teacher_placement_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teacher(id);
ALTER TABLE ONLY public.teacher_salary_history
    ADD CONSTRAINT teacher_salary_history_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.teacher(id);
ALTER TABLE ONLY public.zone_manager_assignment
    ADD CONSTRAINT zone_manager_assignment_manager_id_fkey FOREIGN KEY (manager_id) REFERENCES public.manager(id);
\unrestrict dEAEoWfm5F6fjoZrNggZLE3NaIEQtgQYdEBdlpssTWrVfUm71JIB8v9Wd7ZUhcg
