alter table invoices
    add column payment_method varchar(20) not null default 'CASH'
        check (payment_method in ('CASH', 'CARD', 'UPI'));