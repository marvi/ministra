-- Dagar som skaparen tagit bort ur sin förfrågan (D-041).
--
-- Fortfarande ingen tabell över själva dagarna: listan räknas ut från kyrkoårs-API:t och
-- de här datumen dras ifrån (D-018). Bara undantagen lagras.

create table excluded_day (
    poll_id      bigint not null references poll (id) on delete cascade,
    service_date date   not null,
    primary key (poll_id, service_date)
);
