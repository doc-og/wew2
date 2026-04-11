-- Life Equation Research System
-- Run this in your Supabase SQL editor

-- Enable UUID extension
create extension if not exists "pgcrypto";

-- Cases table
create table if not exists cases (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users(id) on delete cascade not null,
  title text not null,
  system_under_study text not null,
  theory text not null default 'life_equation',
  expected_classification text check (expected_classification in ('alive', 'not_alive', 'unknown')) default 'unknown',
  notes text,
  hypothesis text,
  status text not null default 'draft' check (status in ('draft', 'review', 'ready_to_publish', 'published')),
  intake_data jsonb default '{}',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Case sources table
create table if not exists case_sources (
  id uuid primary key default gen_random_uuid(),
  case_id uuid references cases(id) on delete cascade not null,
  title text not null,
  url text,
  source_type text not null default 'reference' check (source_type in ('reference', 'academic', 'news', 'book', 'dataset', 'other')),
  verification_status text not null default 'pending' check (verification_status in ('pending', 'approved', 'rejected')),
  notes text,
  created_at timestamptz not null default now()
);

-- Case analyses table
create table if not exists case_analyses (
  id uuid primary key default gen_random_uuid(),
  case_id uuid references cases(id) on delete cascade not null,
  theory text not null default 'life_equation',
  analysis_json jsonb not null default '{}',
  final_result text check (final_result in ('alive', 'not_alive', 'inconclusive')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Case drafts table
create table if not exists case_drafts (
  id uuid primary key default gen_random_uuid(),
  case_id uuid references cases(id) on delete cascade not null,
  content text not null default '',
  version integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Indexes
create index if not exists cases_user_id_idx on cases(user_id);
create index if not exists cases_status_idx on cases(status);
create index if not exists cases_updated_at_idx on cases(updated_at desc);
create index if not exists case_sources_case_id_idx on case_sources(case_id);
create index if not exists case_analyses_case_id_idx on case_analyses(case_id);
create index if not exists case_drafts_case_id_idx on case_drafts(case_id);

-- Updated_at trigger function
create or replace function update_updated_at_column()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

-- Triggers
create trigger cases_updated_at
  before update on cases
  for each row execute function update_updated_at_column();

create trigger case_analyses_updated_at
  before update on case_analyses
  for each row execute function update_updated_at_column();

create trigger case_drafts_updated_at
  before update on case_drafts
  for each row execute function update_updated_at_column();

-- Row Level Security
alter table cases enable row level security;
alter table case_sources enable row level security;
alter table case_analyses enable row level security;
alter table case_drafts enable row level security;

-- RLS Policies (single user — just verify ownership)
create policy "Users can manage their own cases"
  on cases for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

create policy "Users can manage sources for their cases"
  on case_sources for all
  using (
    exists (
      select 1 from cases where cases.id = case_sources.case_id and cases.user_id = auth.uid()
    )
  );

create policy "Users can manage analyses for their cases"
  on case_analyses for all
  using (
    exists (
      select 1 from cases where cases.id = case_analyses.case_id and cases.user_id = auth.uid()
    )
  );

create policy "Users can manage drafts for their cases"
  on case_drafts for all
  using (
    exists (
      select 1 from cases where cases.id = case_drafts.case_id and cases.user_id = auth.uid()
    )
  );
