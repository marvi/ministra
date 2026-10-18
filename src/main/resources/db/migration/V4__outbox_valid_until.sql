-- Uttömda mejl gallras när förfrågan de gäller har gått ut (D-030).
--
-- outbox_email har ingen koppling till poll — en sammanfattning kan gälla flera
-- förfrågningar — så kaskaden når den aldrig. Raden bär i stället den senaste
-- giltighetstiden bland förfrågningarna i mejlet.
--
-- Befintliga rader får skapelsedagen plus två månader, den längsta giltighetstid ett
-- skapande kan ge. Det är aldrig tidigare än förfrågans egen dag, bara ibland senare.

alter table outbox_email add column valid_until date;

update outbox_email
   set valid_until = (created_at at time zone 'Europe/Stockholm')::date + interval '2 months';

alter table outbox_email alter column valid_until set not null;
