# Drift

Rootless Podman-quadlet under systemd på egen server. Se D-002, D-014, D-015 och D-034 i
[../docs/decisions.md](../docs/decisions.md).

## Första gången

```bash
loginctl enable-linger "$USER"

mkdir -p ~/.config/ministra ~/.config/containers/systemd
install -m 0600 ministra.env.example ~/.config/ministra/ministra.env
$EDITOR ~/.config/ministra/ministra.env          # fyll i lösenorden

cp ministra.container ~/.config/containers/systemd/
systemctl --user daemon-reload
systemctl --user start ministra
journalctl --user -u ministra -f
```

## Databasen

En egen databas och en egen roll i den befintliga Postgres 16-instansen. Appens roll får
rättigheter bara på sitt eget schema — aldrig superuser.

```sql
create role ministra login password '...';
create database ministra owner ministra;
```

Containern når värden via `host.containers.internal`. Det kräver att `postgresql.conf`
lyssnar på podman-nätets adress och att `pg_hba.conf` släpper in den. Det är en ändring i
en delad produktionsdatabas och görs medvetet, inte av misstag.

Schemat läggs på av Flyway. Om migreringen ska köras av appen vid uppstart måste rollen
få ändra sitt schema — det är fortfarande obeslutat, se
[../docs/product.md](../docs/product.md).

## Uppdatera

En ny tagg (`vX.Y.Z`) bygger och publicerar avbilden till `ghcr.io/marvi/ministra`.
Servern hämtar hem den med `podman auto-update`, eller manuellt:

```bash
podman pull ghcr.io/marvi/ministra:latest
systemctl --user restart ministra
```

## Reverse proxy

Proxyn terminerar TLS och måste sätta `X-Forwarded-For`. Utan den ser alla klienter ut
att komma från proxyn, och hastighetsgränsen på skapandet slår mot alla samtidigt.
Appen bygger däremot aldrig länkar från proxyns headers — den publika adressen kommer
från `MINISTRA_BASE_URL` (D-022).
