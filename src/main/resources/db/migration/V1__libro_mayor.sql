-- Libro mayor de partida doble. Montos en céntimos (bigint): nunca numeric con decimales ni float.

create table cuenta (
    id               varchar(40)  primary key,
    titular          varchar(120) not null,
    moneda           char(3)      not null check (moneda in ('PEN', 'USD')),
    limite_sobregiro bigint       not null check (limite_sobregiro >= 0),
    saldo            bigint       not null default 0,
    interna          boolean      not null
);

create table asiento (
    id            uuid        primary key,
    concepto      text        not null,
    registrado_en timestamptz not null default now()
);

create table movimiento (
    id         bigserial   primary key,
    asiento_id uuid        not null references asiento (id),
    cuenta_id  varchar(40) not null references cuenta (id),
    lado       varchar(5)  not null check (lado in ('DEBE', 'HABER')),
    centimos   bigint      not null check (centimos > 0),
    moneda     char(3)     not null
);
create index movimiento_cuenta on movimiento (cuenta_id);
create index movimiento_asiento on movimiento (asiento_id);

create table operacion (
    id        uuid        primary key,
    tipo      varchar(20) not null,
    clave     varchar(100),
    huella    text,
    fecha     timestamptz not null,
    revierte  uuid        references operacion (id),
    revertida boolean     not null default false
);
-- Segunda barrera contra el doble reverso, por si la aplicación fallara.
create unique index un_solo_reverso on operacion (revierte) where revierte is not null;

create table operacion_asiento (
    operacion_id uuid not null references operacion (id),
    orden        int  not null,
    asiento_id   uuid not null references asiento (id),
    primary key (operacion_id, orden)
);

-- Una clave de idempotencia vigente apunta a una sola operación.
create table idempotencia (
    clave        varchar(100) primary key,
    operacion_id uuid         not null references operacion (id),
    fecha        timestamptz  not null
);

create table credito (
    id        uuid        primary key,
    cuenta_id varchar(40) not null references cuenta (id),
    datos     jsonb       not null
);

insert into cuenta (id, titular, moneda, limite_sobregiro, saldo, interna)
select c.prefijo || '-' || m.moneda, 'Cuadre', m.moneda, 0, 0, true
from (values ('CAJA'), ('ITF'), ('TRANSITO'), ('CARTERA'), ('INTERESES'), ('COMISIONES'), ('DESGRAVAMEN')) as c(prefijo)
cross join (values ('PEN'), ('USD')) as m(moneda);
