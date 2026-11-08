-- Recrea las tablas y carga los datos ficticios de datos_semilla.json.
-- Las claves simples se generan desde 1 y avanzan de uno en uno.
DROP TABLE IF EXISTS CHANGE_HISTORY;
DROP TABLE IF EXISTS CORRECTIVE_ACTION;
DROP TABLE IF EXISTS EVENT;
DROP TABLE IF EXISTS USERS_ROLES;
DROP TABLE IF EXISTS ROLES;
DROP TABLE IF EXISTS USERS;

CREATE TABLE USERS (
    USER_ID INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) PRIMARY KEY,
    USERNAME VARCHAR(30) UNIQUE NOT NULL
);

CREATE TABLE ROLES (
    ROLE_ID INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) PRIMARY KEY,
    ROLE_NAME VARCHAR(30) UNIQUE NOT NULL
);

CREATE TABLE USERS_ROLES (
    USER_ID INT NOT NULL,
    ROLE_ID INT NOT NULL,
    PRIMARY KEY (USER_ID, ROLE_ID),
    FOREIGN KEY (USER_ID) REFERENCES USERS(USER_ID),
    FOREIGN KEY (ROLE_ID) REFERENCES ROLES(ROLE_ID)
);

CREATE TABLE EVENT (
    EVENT_ID INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) PRIMARY KEY,
    TITLE VARCHAR(120) NOT NULL,
    DESCRIPTION VARCHAR(2000) NOT NULL,
    EVENT_DATE DATE NOT NULL,
    EVENT_AREA VARCHAR(125) NOT NULL,
    STATUS VARCHAR(30) NOT NULL,
    REPORTER_ID INT NOT NULL,
    FOREIGN KEY (REPORTER_ID) REFERENCES USERS(USER_ID)
);

CREATE TABLE CORRECTIVE_ACTION (
    ACTION_ID INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) PRIMARY KEY,
    DESCRIPTION VARCHAR(500) NOT NULL,
    RESPONSIBLE VARCHAR(30) NOT NULL,
    TARGET_DATE TIMESTAMP NOT NULL,
    STATUS VARCHAR(30) NOT NULL,
    EVENT_ID INT NOT NULL,
    FOREIGN KEY (EVENT_ID) REFERENCES EVENT(EVENT_ID)
);

CREATE TABLE CHANGE_HISTORY (
    CHANGE_HISTORY_ID INT GENERATED ALWAYS AS IDENTITY (START WITH 1 INCREMENT BY 1) PRIMARY KEY,
    CHANGE_DATE TIMESTAMP NOT NULL,
    CHANGE_DESCRIPTION VARCHAR(500) NOT NULL,
    USER_ID INT NOT NULL,
    EVENT_ID INT NOT NULL,
    FOREIGN KEY (USER_ID) REFERENCES USERS(USER_ID),
    FOREIGN KEY (EVENT_ID) REFERENCES EVENT(EVENT_ID)
);

-- Roles y usuarios de prueba definidos en el caso práctico.
INSERT INTO ROLES (ROLE_NAME) VALUES
    ('reportante'),
    ('revisor');

INSERT INTO USERS (USERNAME) VALUES
    ('reportante1'),
    ('reportante2'),
    ('revisor1');

INSERT INTO USERS_ROLES (USER_ID, ROLE_ID) VALUES
    (1, 1),
    (2, 1),
    (3, 2);

-- Eventos en los tres estados del flujo, distribuidos entre ambos reportantes.
INSERT INTO EVENT (TITLE, DESCRIPTION, EVENT_DATE, EVENT_AREA, STATUS, REPORTER_ID) VALUES
    ('Derrame menor de aceite en patio de bodega',
     'Se observó una mancha de aceite de aproximadamente un metro junto a la zona de carga. Se delimitó con conos.',
     DATE '2026-10-28', 'Bodega', 'reportado', 1),
    ('Extintor con fecha de inspección vencida',
     'El extintor del pasillo B tiene la etiqueta de inspección vencida desde septiembre.',
     DATE '2026-10-30', 'Planta Norte', 'en revisión', 2),
    ('Cable de extensión cruzando pasillo',
     'Un cable de extensión cruza el pasillo principal de oficinas sin protección. Riesgo de tropiezo.',
     DATE '2026-10-22', 'Administración', 'cerrado', 1),
    ('Baranda suelta en plataforma de acceso',
     'La baranda del lado oriente de la plataforma de acceso se mueve al apoyarse.',
     DATE '2026-11-02', 'Planta Sur', 'reportado', 2);

-- El evento en revisión tiene una acción pendiente; el cerrado, una completada.
INSERT INTO CORRECTIVE_ACTION (DESCRIPTION, RESPONSIBLE, TARGET_DATE, STATUS, EVENT_ID) VALUES
    ('Reemplazar el extintor del pasillo B y actualizar el registro de inspección',
     'Camila Ficticia', TIMESTAMP '2026-11-15 00:00:00', 'pendiente', 2),
    ('Instalar canaleta pasacables en el pasillo principal',
     'Diego Ficticio', TIMESTAMP '2026-10-30 00:00:00', 'completada', 3);

-- TIMESTAMP conserva las horas locales del JSON (UTC-03:00), sin zona horaria.
-- Todas las operaciones del historial fueron realizadas por revisor1 (USER_ID = 3).
INSERT INTO CHANGE_HISTORY (CHANGE_DATE, CHANGE_DESCRIPTION, USER_ID, EVENT_ID) VALUES
    (TIMESTAMP '2026-10-31 09:15:00',
     'Estado: reportado -> en revisión', 3, 2),
    (TIMESTAMP '2026-10-31 09:20:00',
     'Acción creada: Reemplazar el extintor del pasillo B (responsable Camila Ficticia, fecha objetivo 2026-11-15)', 3, 2),
    (TIMESTAMP '2026-10-23 10:00:00',
     'Estado: reportado -> en revisión', 3, 3),
    (TIMESTAMP '2026-10-23 10:05:00',
     'Acción creada: Instalar canaleta pasacables (responsable Diego Ficticio, fecha objetivo 2026-10-30)', 3, 3),
    (TIMESTAMP '2026-10-29 16:40:00',
     'Acción completada: Instalar canaleta pasacables', 3, 3),
    (TIMESTAMP '2026-10-29 16:45:00',
     'Estado: en revisión -> cerrado', 3, 3);
