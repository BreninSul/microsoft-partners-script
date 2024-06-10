create schema microsoft;

create table microsoft.partners
(
    id                   bigserial primary key,
    microsoft_id         uuid generated always as ( (raw ->> 'id')::uuid ) stored,
    microsoft_partner_id uuid generated always as ( (raw ->> 'partnerId')::uuid ) stored,
    name                 text generated always as ( (raw ->> 'name') ) stored,
    linkedin_link        text generated always as ( (raw ->> 'linkedInOrganizationProfile') ) stored,
    microsoft_link       text generated always as ( (
        'https://appsource.microsoft.com/en-us/marketplace/partner-dir/' || (raw ->> 'partnerId') ||
        '/overview') ) stored,
    page_number          int,
    country              text,
    created_at           timestamp default current_timestamp,
    raw                  jsonb not null
);

create unique index microsoft_id_unique on microsoft.partners (microsoft_id);

select
            ROW_NUMBER() OVER(ORDER BY p.country,p.page_number,p.id) AS number,
            p.name,
            case when p.microsoft_link is not null and length(p.microsoft_link)>0 then '=HYPERLINK("'||p.microsoft_link||'")' else '' end as microsoft_link,
            case when p.linkedin_link is not null and length(p.linkedin_link)>0 then '=HYPERLINK("'||p.linkedin_link||'")' else '' end as linkedin_link,
            p.microsoft_id,
            p.microsoft_partner_id
from microsoft.partners p
order by p.country,p.page_number,p.id