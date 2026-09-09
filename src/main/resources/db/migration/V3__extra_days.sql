-- Dagar skaparen lagt till för hand (D-043).
--
-- Spegelbilden av excluded_day. En församling kan fira något som inte finns i Svenska
-- kyrkans kyrkoår — ett lokalt helgon, en särskild högtid. Fortfarande ingen tabell över
-- dagarna själva: förfrågan bär bara justeringar av den härledda listan (D-018).

create table extra_day (
    poll_id      bigint not null references poll (id) on delete cascade,
    service_date date   not null,
    primary key (poll_id, service_date)
);
