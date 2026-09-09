-- Ministra, ursprungligt schema.
--
-- Ingen tabell för söndagar: de räknas fram från kyrkoårs-API:t och svarsraden bär sitt
-- eget datum (D-018).

create table poll (
    id              bigserial primary key,
    response_token  varchar(64)  not null unique,
    admin_token     varchar(64)  not null unique,
    title           varchar(120) not null,
    comment_text    varchar(1000),
    start_date      date         not null,
    end_date        date         not null,
    valid_until     date         not null,
    creator_name    varchar(80)  not null,
    creator_email   varchar(254) not null,
    created_at      timestamptz  not null
);

-- Gallringsjobbet frågar på utgångna förfrågningar varje natt.
create index poll_valid_until_idx on poll (valid_until);

create table participant (
    id           bigserial primary key,
    poll_id      bigint      not null references poll (id) on delete cascade,
    name         varchar(80) not null,
    -- Normaliserad form: trimmad och gemener. Unikheten vilar på den här, inte på name,
    -- så att "anna" och "Anna " inte kan samsas i samma förfrågan (D-009).
    name_key     varchar(80) not null,
    submitted_at timestamptz not null,
    constraint participant_unique_name_per_poll unique (poll_id, name_key)
);

create index participant_poll_idx on participant (poll_id);

create table response (
    id             bigserial primary key,
    participant_id bigint      not null references participant (id) on delete cascade,
    service_date   date        not null,
    availability   varchar(16) not null,
    -- Sätts när svaret tagits med i en daglig sammanfattning, i samma transaktion som
    -- outbox-raden skrivs (D-030).
    notified_at    timestamptz,
    constraint response_unique_date_per_participant unique (participant_id, service_date)
);

create index response_participant_idx on response (participant_id);

-- Sammanfattningsjobbet letar bara efter orapporterade svar, så indexet behöver bara
-- täcka dem.
create index response_unnotified_idx on response (participant_id) where notified_at is null;

-- All utgående post går genom den här tabellen. Ingenting annat i appen skickar mejl
-- (D-030). Raden raderas när mejlet gått iväg — brödtexten innehåller förnamn och ska
-- inte bli ett arkiv (D-028).
create table outbox_email (
    id              bigserial primary key,
    recipient       varchar(254) not null,
    subject         varchar(200) not null,
    body            text         not null,
    created_at      timestamptz  not null,
    next_attempt_at timestamptz  not null,
    attempts        integer      not null default 0,
    last_error      varchar(500)
);

create index outbox_email_next_attempt_idx on outbox_email (next_attempt_at);
