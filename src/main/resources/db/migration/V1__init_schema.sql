-- User table

CREATE TABLE users (
    id UUID NOT NULL,
    firstname VARCHAR(50) NOT NULL,
    lastname VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role)
);

-- Appointment table

CREATE TABLE appointments (
    id UUID NOT NULL,
    customer_id UUID NOT NULL,
    professional_id UUID NOT NULL,
    treatment_id UUID NOT NULL,
    salon_id UUID NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_appointments PRIMARY KEY (id)
);

-- Availability table

CREATE TABLE availabilities (
    id UUID NOT NULL,
    professional_id UUID NOT NULL,
    day VARCHAR(20) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_availabilities PRIMARY KEY (id)
);

-- Customer profile table
CREATE TABLE customer_profiles (
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_customer_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_customer_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Professional profile table
CREATE TABLE professional_profiles (
    user_id UUID NOT NULL,
    salon_id UUID NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_professional_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_professional_profiles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Salon table
CREATE TABLE salons (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    municipality VARCHAR(255),
    neighborhood VARCHAR(255) NOT NULL,
    street VARCHAR(255),
    location_hint VARCHAR(500),
    description TEXT NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    owner_id UUID NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_salons PRIMARY KEY (id),
    CONSTRAINT fk_salons_owner
        FOREIGN KEY (owner_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- Treatment table
CREATE TABLE treatments (
    id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    price NUMERIC(12) NOT NULL,
    duration_in_minutes INTEGER NOT NULL,
    professional_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_treatments PRIMARY KEY (id),
    CONSTRAINT chk_treatments_price CHECK (price >= 0),
    CONSTRAINT chk_treatments_duration CHECK (duration_in_minutes >= 5),
    CONSTRAINT fk_treatments_professional
        FOREIGN KEY (professional_id) REFERENCES professional_profiles(user_id)
        ON DELETE CASCADE
);

