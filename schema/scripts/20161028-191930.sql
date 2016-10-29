create table key_analysis (
  id bigserial primary key,
  observed_at timestamp with time zone DEFAULT NOW(),
  raw_key text not null,
  domain text not null,
  email text not null,
  industry text,
  size text,
  key_created_at timestamp with time zone NOT NULL,
  key_expires_at timestamp with time zone,
  key_algorithm text not null,
  key_bit_strength text not null,
  key_revoked boolean not null default false,
  key_user_attributes text,
  key_version bigint not null
);

CREATE INDEX key_domain_idx ON key_analysis(domain);
CREATE INDEX key_company_industry_idx ON key_analysis(industry);
CREATE INDEX key_company_size_idx ON key_analysis(size);
