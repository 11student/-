-- MySQL core schema DRAFT for review; no database has been created or migrated.
-- Minimum syntax target: MySQL 8.0.16, InnoDB, utf8mb4.
-- Review the exact server/RuoYi versions, UTC connection time zone, identity mapping,
-- permissions, retention and migration/rollback plan before any execution.
-- Intentionally omits CREATE DATABASE, USE, system tables, seed data and DROP statements.

CREATE TABLE edu_term (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_term_id_org (id, organization_id),
    UNIQUE KEY uq_edu_term_org_code (organization_id, code),
    KEY idx_edu_term_org_dates (organization_id, starts_on, ends_on),
    CONSTRAINT chk_edu_term_dates CHECK (ends_on >= starts_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_official_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_official_course_id_org (id, organization_id),
    UNIQUE KEY uq_edu_official_course_org_code (organization_id, code),
    CONSTRAINT chk_edu_official_course_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_space (
    id BIGINT NOT NULL AUTO_INCREMENT,
    official_course_id BIGINT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_edu_course_space_owner_status (owner_user_id, status),
    KEY idx_edu_course_space_official (official_course_id),
    CONSTRAINT fk_edu_course_space_official FOREIGN KEY (official_course_id)
        REFERENCES edu_official_course (id),
    CONSTRAINT chk_edu_course_space_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    parent_unit_id BIGINT NULL,
    unit_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_course_unit_id_space (id, course_space_id),
    KEY idx_edu_course_unit_space_parent_sort (course_space_id, parent_unit_id, sort_order),
    KEY idx_edu_course_unit_parent_space (parent_unit_id, course_space_id),
    CONSTRAINT fk_edu_course_unit_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT fk_edu_course_unit_parent FOREIGN KEY (parent_unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id),
    CONSTRAINT chk_edu_course_unit_kind CHECK (unit_kind IN ('CHAPTER', 'SECTION')),
    CONSTRAINT chk_edu_course_unit_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_knowledge_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_knowledge_point_space_code (course_space_id, code),
    KEY idx_edu_knowledge_point_unit_space (unit_id, course_space_id),
    CONSTRAINT fk_edu_knowledge_point_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT fk_edu_knowledge_point_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_teaching_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    official_course_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    class_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_teaching_class_org_term_code (organization_id, term_id, code),
    KEY idx_edu_teaching_class_term_org (term_id, organization_id),
    KEY idx_edu_teaching_class_official_org (official_course_id, organization_id),
    CONSTRAINT fk_edu_teaching_class_term_org FOREIGN KEY (term_id, organization_id)
        REFERENCES edu_term (id, organization_id),
    CONSTRAINT fk_edu_teaching_class_official_org FOREIGN KEY (official_course_id, organization_id)
        REFERENCES edu_official_course (id, organization_id),
    CONSTRAINT chk_edu_teaching_class_kind CHECK (class_kind IN ('FORMAL', 'TRIAL')),
    CONSTRAINT chk_edu_teaching_class_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_class_teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    teacher_role VARCHAR(20) NOT NULL,
    can_publish TINYINT(1) NOT NULL DEFAULT 0,
    can_grade TINYINT(1) NOT NULL DEFAULT 0,
    can_export TINYINT(1) NOT NULL DEFAULT 0,
    valid_from DATETIME(3) NOT NULL,
    valid_to DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN valid_to IS NULL THEN user_id ELSE NULL END) STORED,
    granted_by_user_id BIGINT NOT NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_teacher_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_teacher_user_dates (user_id, valid_from, valid_to),
    CONSTRAINT fk_edu_class_teacher_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_teacher_role CHECK (teacher_role IN ('LEAD', 'CO_TEACHER')),
    CONSTRAINT chk_edu_class_teacher_flags CHECK
        (can_publish IN (0, 1) AND can_grade IN (0, 1) AND can_export IN (0, 1)),
    CONSTRAINT chk_edu_class_teacher_dates CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_class_student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(3) NOT NULL,
    left_at DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN left_at IS NULL THEN user_id ELSE NULL END) STORED,
    recorded_by_user_id BIGINT NOT NULL,
    left_by_user_id BIGINT NULL,
    leave_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_student_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_student_user_dates (user_id, joined_at, left_at),
    CONSTRAINT fk_edu_class_student_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_student_dates CHECK (left_at IS NULL OR left_at > joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_class_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    bound_by_user_id BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    active_space_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN ended_at IS NULL THEN course_space_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_binding_id_space (id, course_space_id),
    UNIQUE KEY uq_edu_binding_active_pair (teaching_class_id, active_space_id),
    KEY idx_edu_binding_space_dates (course_space_id, published_at, ended_at),
    CONSTRAINT fk_edu_binding_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT fk_edu_binding_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_edu_binding_dates CHECK (ended_at IS NULL OR ended_at > published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    resource_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_id_space (id, course_space_id),
    KEY idx_edu_resource_space_status (course_space_id, status, id),
    CONSTRAINT fk_edu_resource_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_edu_resource_kind CHECK (resource_kind IN ('FILE', 'LINK')),
    CONSTRAINT chk_edu_resource_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    storage_key VARCHAR(512) NULL,
    external_uri VARCHAR(1024) NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    mime_type VARCHAR(120) NULL,
    byte_size BIGINT UNSIGNED NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_version_id_resource (id, resource_id),
    UNIQUE KEY uq_edu_resource_version_no (resource_id, version_no),
    CONSTRAINT fk_edu_resource_version_resource FOREIGN KEY (resource_id)
        REFERENCES edu_resource (id),
    CONSTRAINT chk_edu_resource_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_resource_version_source CHECK
        ((storage_key IS NOT NULL AND external_uri IS NULL) OR
         (storage_key IS NULL AND external_uri IS NOT NULL)),
    CONSTRAINT chk_edu_resource_version_status CHECK
        (processing_status IN ('PENDING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource_class_release (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    released_by_user_id BIGINT NOT NULL,
    released_at DATETIME(3) NOT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    revoked_by_user_id BIGINT NULL,
    revoke_reason VARCHAR(500) NULL,
    active_resource_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN revoked_at IS NULL THEN resource_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_release_active_resource (binding_id, active_resource_id),
    KEY idx_edu_release_binding_space (binding_id, course_space_id),
    KEY idx_edu_release_resource_space (resource_id, course_space_id),
    KEY idx_edu_release_version_resource (resource_version_id, resource_id),
    KEY idx_edu_release_open_window (binding_id, opens_at, closes_at),
    CONSTRAINT fk_edu_release_binding_space FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_release_resource_space FOREIGN KEY (resource_id, course_space_id)
        REFERENCES edu_resource (id, course_space_id),
    CONSTRAINT fk_edu_release_version_resource FOREIGN KEY (resource_version_id, resource_id)
        REFERENCES edu_resource_version (id, resource_id),
    CONSTRAINT chk_edu_release_window CHECK (closes_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_release_revoke CHECK (revoked_at IS NULL OR revoked_at >= released_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
