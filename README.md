Application to get list of Microsoft partners and save them to PostgresSQL DB.

Requirements 
1. PostgresSQL (tested on 16.1)
2. Gradle 8.5
3. Java 21

App will make api requests to microsoft API (https://main.prod.marketplacepartnerdirectory.azure.com/api/partners)
with filters and pagination in 1 thread (WAF is blocking requests in parallel mode).

Microsoft provide only first 100 results, so there is filter by all countries in cycle performed to get more results.
Additional filters can be added to get more results

## Build

To compile project such env. variables should be set up:

|      Env. variable      |                           Description                            | Possible values |
|:-----------------------:|:----------------------------------------------------------------:|:---------------:|
| GITHUB_PACKAGE_USERNAME | Any git account username - used to access github packages (open) |      text       |
|  GITHUB_PACKAGE_TOKEN   |   Any git account pass - used to access github packages (open)   |      text       |

execute `gradle bootJar`

## Configuration of app


Use env. variables to change default settings:

|       Env. variable        |                          Description                          | Possible values |
|:--------------------------:|:-------------------------------------------------------------:|:---------------:|
| spring.datasource.password |                            DB pass                            |      text       |
| spring.datasource.username |                            DB user                            |      text       |
| spring.datasource.username | DB JDBC uri   like `jdbc:postgresql://127.0.0.1:6433/ordinal` |      text       |
|            sort            |                           Sort type                           |   Int   0..2    |
|            max             |        Max results for filter (100 is Microsoft limit)        |       Int       |
|            link            |                       Microsoft API uri                       |       URI       |
|      additionalFilter      |         Microsoft filter like `services=Integration;`         |      text       |

## Preparation

Create table for result
```postgresql
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

```


## Run

execute `java -jar $path-to-jar`

## Get results

To get results for importing to Excel query like 

```postgresql

select
            ROW_NUMBER() OVER(ORDER BY p.country,p.page_number,p.id) AS number,
            p.name,
            case when p.microsoft_link is not null and length(p.microsoft_link)>0 then '=HYPERLINK("'||p.microsoft_link||'")' else '' end as microsoft_link,
            case when p.linkedin_link is not null and length(p.linkedin_link)>0 then '=HYPERLINK("'||p.linkedin_link||'")' else '' end as linkedin_link,
            p.microsoft_id,
            p.microsoft_partner_id
from microsoft.partners p
order by p.country,p.page_number,p.id

```

can be performed. SQL IDE like JetBrains DataGrip allows to export result of query as xlsx