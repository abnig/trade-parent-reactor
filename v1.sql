-- public.batch_job_instance definition
-- Drop table
-- DROP TABLE public.batch_job_instance;

create table public.batch_job_instance (
	job_instance_id int8 not null,
	"version" int8 null,
	job_name varchar(100) not null,
	job_key varchar(32) not null,
	constraint batch_job_instance_job_instance_id_not_null not null job_instance_id,
	constraint batch_job_instance_job_key_not_null not null job_key,
	constraint batch_job_instance_job_name_not_null not null job_name,
	constraint batch_job_instance_pkey primary key (job_instance_id),
	constraint job_inst_un unique (job_name,
job_key)
);
-- Permissions

alter table public.batch_job_instance owner to postgres;

grant all on
table public.batch_job_instance to postgres;
-- public.ledger_balances definition
-- Drop table
-- DROP TABLE public.ledger_balances;

create table public.ledger_balances (
	ledger_balance_id uuid not null,
	balance_type varchar(100) not null,
	net_balance numeric(15, 6) not null,
	posting_date date not null,
	file_name varchar(500) not null,
	create_date_time timestamp not null,
	constraint ledger_balance_pkey primary key (ledger_balance_id),
	constraint ledger_balances_balance_type_not_null not null balance_type,
	constraint ledger_balances_create_date_time_not_null not null create_date_time,
	constraint ledger_balances_file_name_not_null not null file_name,
	constraint ledger_balances_ledger_balance_id_not_null not null ledger_balance_id,
	constraint ledger_balances_net_balance_not_null not null net_balance,
	constraint ledger_balances_posting_date_not_null not null posting_date
);
-- Permissions

alter table public.ledger_balances owner to postgres;

grant all on
table public.ledger_balances to postgres;
-- public.ledger_records definition
-- Drop table
-- DROP TABLE public.ledger_records;

create table public.ledger_records (
	ledger_record_id uuid not null,
	particulars varchar(1000) not null,
	posting_date date not null,
	cost_center varchar(50) not null,
	voucher_type varchar(50) null,
	debit numeric(15, 6) not null,
	credit numeric(15, 6) not null,
	net_balance numeric(15, 6) null,
	file_name varchar(500) not null,
	create_date_time timestamp not null,
	constraint ledger_records_cost_center_not_null not null cost_center,
	constraint ledger_records_create_date_time_not_null not null create_date_time,
	constraint ledger_records_credit_not_null not null credit,
	constraint ledger_records_debit_not_null not null debit,
	constraint ledger_records_file_name_not_null not null file_name,
	constraint ledger_records_ledger_id_not_null not null ledger_record_id,
	constraint ledger_records_particulars_not_null not null particulars,
	constraint ledger_records_pkey primary key (ledger_record_id),
	constraint ledger_records_posting_date_not_null not null posting_date
);
-- Permissions

alter table public.ledger_records owner to postgres;

grant all on
table public.ledger_records to postgres;
-- public.mutual_fund_broker_account definition
-- Drop table
-- DROP TABLE public.mutual_fund_broker_account;

create table public.mutual_fund_broker_account (
	broker_account_id int8 default nextval('broker_account_broker_account_id_seq'::regclass) not null,
	broker_name varchar(255) not null,
	account_id varchar(100) not null,
	create_date timestamp default CURRENT_TIMESTAMP not null,
	update_date timestamp default CURRENT_TIMESTAMP not null,
	constraint broker_account_account_id_not_null not null account_id,
	constraint broker_account_broker_account_id_not_null not null broker_account_id,
	constraint broker_account_broker_name_not_null not null broker_name,
	constraint broker_account_create_date_not_null not null create_date,
	constraint broker_account_pkey primary key (broker_account_id),
	constraint broker_account_update_date_not_null not null update_date
);
-- Permissions

alter table public.mutual_fund_broker_account owner to postgres;

grant all on
table public.mutual_fund_broker_account to postgres;
-- public.spring_ai_chat_memory definition
-- Drop table
-- DROP TABLE public.spring_ai_chat_memory;

create table public.spring_ai_chat_memory (
	conversation_id varchar(36) not null,
	"content" text not null,
	"type" varchar(10) not null,
	"timestamp" timestamp not null,
	sequence_id int8 not null,
	constraint spring_ai_chat_memory_content_not_null not null content,
	constraint spring_ai_chat_memory_conversation_id_not_null not null conversation_id,
	constraint spring_ai_chat_memory_pkey primary key (conversation_id,
sequence_id),
	constraint spring_ai_chat_memory_sequence_id_not_null not null sequence_id,
	constraint spring_ai_chat_memory_timestamp_not_null not null "timestamp",
	constraint spring_ai_chat_memory_type_not_null not null type
);
-- Permissions

alter table public.spring_ai_chat_memory owner to postgres;

grant all on
table public.spring_ai_chat_memory to postgres;
-- public.trade_records definition
-- Drop table
-- DROP TABLE public.trade_records;

create table public.trade_records (
	id uuid not null,
	symbol varchar(50) not null,
	isin varchar(50) null,
	trade_date date not null,
	exchange varchar(50) not null,
	segment varchar(50) null,
	series varchar(50) null,
	trade_type varchar(10) null,
	auction bool default false null,
	quantity numeric(15, 6) not null,
	price numeric(15, 6) not null,
	trade_id int8 null,
	order_id varchar(100) null,
	order_execution_time timestamp null,
	created_at timestamp default CURRENT_TIMESTAMP null,
	constraint trade_records_exchange_not_null not null exchange,
	constraint trade_records_id_not_null not null id,
	constraint trade_records_pkey primary key (id),
	constraint trade_records_price_not_null not null price,
	constraint trade_records_quantity_not_null not null quantity,
	constraint trade_records_symbol_not_null not null symbol,
	constraint trade_records_trade_date_not_null not null trade_date,
	constraint trade_records_trade_type_check check (((trade_type)::text = any ((array['buy'::character varying,
'sell'::character varying])::text[])))
);
-- Permissions

alter table public.trade_records owner to postgres;

grant all on
table public.trade_records to postgres;
-- public.vector_store definition
-- Drop table
-- DROP TABLE public.vector_store;

create table public.vector_store (
	id uuid default uuid_generate_v4() not null,
	"content" text null,
	metadata json null,
	embedding public.vector null,
	constraint vector_store_id_not_null not null id,
	constraint vector_store_pkey primary key (id)
);

create index spring_ai_vector_index on
public.vector_store
    using hnsw (embedding vector_cosine_ops);
-- Permissions

alter table public.vector_store owner to postgres;

grant all on
table public.vector_store to postgres;
-- public.batch_job_execution definition
-- Drop table
-- DROP TABLE public.batch_job_execution;

create table public.batch_job_execution (
	job_execution_id int8 not null,
	"version" int8 null,
	job_instance_id int8 not null,
	create_time timestamp not null,
	start_time timestamp null,
	end_time timestamp null,
	status varchar(10) null,
	exit_code varchar(2500) null,
	exit_message varchar(2500) null,
	last_updated timestamp null,
	constraint batch_job_execution_create_time_not_null not null create_time,
	constraint batch_job_execution_job_execution_id_not_null not null job_execution_id,
	constraint batch_job_execution_job_instance_id_not_null not null job_instance_id,
	constraint batch_job_execution_pkey primary key (job_execution_id),
	constraint job_inst_exec_fk foreign key (job_instance_id) references public.batch_job_instance(job_instance_id)
);
-- Permissions

alter table public.batch_job_execution owner to postgres;

grant all on
table public.batch_job_execution to postgres;
-- public.batch_job_execution_context definition
-- Drop table
-- DROP TABLE public.batch_job_execution_context;

create table public.batch_job_execution_context (
	job_execution_id int8 not null,
	short_context varchar(2500) not null,
	serialized_context text null,
	constraint batch_job_execution_context_job_execution_id_not_null not null job_execution_id,
	constraint batch_job_execution_context_pkey primary key (job_execution_id),
	constraint batch_job_execution_context_short_context_not_null not null short_context,
	constraint job_exec_ctx_fk foreign key (job_execution_id) references public.batch_job_execution(job_execution_id)
);
-- Permissions

alter table public.batch_job_execution_context owner to postgres;

grant all on
table public.batch_job_execution_context to postgres;
-- public.batch_job_execution_params definition
-- Drop table
-- DROP TABLE public.batch_job_execution_params;

create table public.batch_job_execution_params (
	job_execution_id int8 not null,
	parameter_name varchar(100) not null,
	parameter_type varchar(100) not null,
	parameter_value varchar(2500) null,
	identifying bpchar(1) not null,
	constraint batch_job_execution_params_identifying_not_null not null identifying,
	constraint batch_job_execution_params_job_execution_id_not_null not null job_execution_id,
	constraint batch_job_execution_params_parameter_name_not_null not null parameter_name,
	constraint batch_job_execution_params_parameter_type_not_null not null parameter_type,
	constraint job_exec_params_fk foreign key (job_execution_id) references public.batch_job_execution(job_execution_id)
);
-- Permissions

alter table public.batch_job_execution_params owner to postgres;

grant all on
table public.batch_job_execution_params to postgres;
-- public.batch_step_execution definition
-- Drop table
-- DROP TABLE public.batch_step_execution;

create table public.batch_step_execution (
	step_execution_id int8 not null,
	"version" int8 not null,
	step_name varchar(100) not null,
	job_execution_id int8 not null,
	create_time timestamp not null,
	start_time timestamp null,
	end_time timestamp null,
	status varchar(10) null,
	commit_count int8 null,
	read_count int8 null,
	filter_count int8 null,
	write_count int8 null,
	read_skip_count int8 null,
	write_skip_count int8 null,
	process_skip_count int8 null,
	rollback_count int8 null,
	exit_code varchar(2500) null,
	exit_message varchar(2500) null,
	last_updated timestamp null,
	constraint batch_step_execution_create_time_not_null not null create_time,
	constraint batch_step_execution_job_execution_id_not_null not null job_execution_id,
	constraint batch_step_execution_pkey primary key (step_execution_id),
	constraint batch_step_execution_step_execution_id_not_null not null step_execution_id,
	constraint batch_step_execution_step_name_not_null not null step_name,
	constraint batch_step_execution_version_not_null not null version,
	constraint job_exec_step_fk foreign key (job_execution_id) references public.batch_job_execution(job_execution_id)
);
-- Permissions

alter table public.batch_step_execution owner to postgres;

grant all on
table public.batch_step_execution to postgres;
-- public.batch_step_execution_context definition
-- Drop table
-- DROP TABLE public.batch_step_execution_context;

create table public.batch_step_execution_context (
	step_execution_id int8 not null,
	short_context varchar(2500) not null,
	serialized_context text null,
	constraint batch_step_execution_context_pkey primary key (step_execution_id),
	constraint batch_step_execution_context_short_context_not_null not null short_context,
	constraint batch_step_execution_context_step_execution_id_not_null not null step_execution_id,
	constraint step_exec_ctx_fk foreign key (step_execution_id) references public.batch_step_execution(step_execution_id)
);
-- Permissions

alter table public.batch_step_execution_context owner to postgres;

grant all on
table public.batch_step_execution_context to postgres;
-- public.mutual_fund definition
-- Drop table
-- DROP TABLE public.mutual_fund;

create table public.mutual_fund (
	mutual_fund_id int8 generated always as identity( increment by 1 minvalue 1 maxvalue 9223372036854775807 start 1 cache 1 no cycle) not null,
	broker_account_id int8 not null,
	mutual_fund_name varchar(255) not null,
	create_date timestamp default CURRENT_TIMESTAMP not null,
	update_date timestamp default CURRENT_TIMESTAMP not null,
	constraint mutual_fund_broker_account_id_not_null not null broker_account_id,
	constraint mutual_fund_create_date_not_null not null create_date,
	constraint mutual_fund_mutual_fund_id_not_null not null mutual_fund_id,
	constraint mutual_fund_mutual_fund_name_not_null not null mutual_fund_name,
	constraint mutual_fund_pkey primary key (mutual_fund_id),
	constraint mutual_fund_update_date_not_null not null update_date,
	constraint fk_mutual_fund_broker_account foreign key (broker_account_id) references public.mutual_fund_broker_account(broker_account_id)
);
-- Permissions

alter table public.mutual_fund owner to postgres;

grant all on
table public.mutual_fund to postgres;
-- public.mutual_fund_txn definition
-- Drop table
-- DROP TABLE public.mutual_fund_txn;

create table public.mutual_fund_txn (
	mutual_fund_txn_id int8 generated always as identity( increment by 1 minvalue 1 maxvalue 9223372036854775807 start 1 cache 1 no cycle) not null,
	mutual_fund_id int8 not null,
	amount numeric(18, 2) not null,
	create_date timestamp default CURRENT_TIMESTAMP not null,
	update_date timestamp default CURRENT_TIMESTAMP not null,
	txn_date timestamp not null,
	units numeric(18, 3) default 0 not null,
	avg_price numeric(18, 3) default 0 not null,
	txn_type varchar default 'BUY'::character varying not null,
	constraint mutual_fund_txn_amount_not_null not null amount,
	constraint mutual_fund_txn_avg_price_not_null not null avg_price,
	constraint mutual_fund_txn_create_date_not_null not null create_date,
	constraint mutual_fund_txn_mutual_fund_id_not_null not null mutual_fund_id,
	constraint mutual_fund_txn_mutual_fund_txn_id_not_null not null mutual_fund_txn_id,
	constraint mutual_fund_txn_pkey primary key (mutual_fund_txn_id),
	constraint mutual_fund_txn_txn_date_not_null not null txn_date,
	constraint mutual_fund_txn_txn_type_not_null not null txn_type,
	constraint mutual_fund_txn_units_not_null not null units,
	constraint mutual_fund_txn_update_date_not_null not null update_date,
	constraint fk_mutual_fund_txn_mutual_fund foreign key (mutual_fund_id) references public.mutual_fund(mutual_fund_id)
);
-- Permissions

alter table public.mutual_fund_txn owner to postgres;

grant all on
table public.mutual_fund_txn to postgres;
-- public.mutual_fund_value definition
-- Drop table
-- DROP TABLE public.mutual_fund_value;

create table public.mutual_fund_value (
	val_id int8 generated always as identity( increment by 1 minvalue 1 maxvalue 9223372036854775807 start 1 cache 1 no cycle) not null,
	mutual_fund_id int8 not null,
	total_value numeric(18, 2) not null,
	value_as_of_date timestamp not null,
	constraint mutual_fund_value_mutual_fund_id_not_null not null mutual_fund_id,
	constraint mutual_fund_value_pkey primary key (val_id),
	constraint mutual_fund_value_total_value_not_null not null total_value,
	constraint mutual_fund_value_val_id_not_null not null val_id,
	constraint mutual_fund_value_value_as_of_date_not_null not null value_as_of_date,
	constraint fk_mutual_fund_value_mutual_fund foreign key (mutual_fund_id) references public.mutual_fund(mutual_fund_id)
);
-- Permissions

alter table public.mutual_fund_value owner to postgres;

grant all on
table public.mutual_fund_value to postgres;
