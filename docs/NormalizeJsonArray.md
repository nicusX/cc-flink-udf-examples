## NormalizeJsonArray

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

User Defined Table Function (UDTF) source code:
[NormalizeJsonArray](../src/main/java/io/confluent/flink/examples/udf/table/NormalizeJsonArray.java)

This UDTF demonstrates how you can normalize a JSON payload emitting multiple records for each nested element.

In this example, the UDTF expands a field containing a simple JSON array, which is roughly what the Flink system function `UNNEST`
does. The implementation can be easily expanded with a more complex logic, for example looking for specific elements or fields to extract.

### Register the User Defined Table Function (UDTF)

Replace `<artifact-id>` with the ID of the JAR artifact you uploaded.
The artifact ID is a string starting with `cfa-` like `cfa-abc1234`.

```sql
CREATE FUNCTION `normalize_json_emails`
  AS 'io.confluent.flink.examples.udf.table.NormalizeJsonArray'
  USING JAR 'confluent-artifact://<artifact-id>'
```

Verify registration:
```sql
DESCRIBE FUNCTION EXTENDED `normalize_json_emails`
```

### Prepare input data

Create a *faker* table to generate customers with multiple emails
(note that faker only generates fixed-size arrays, so we also generate the number of emails to retain):
```sql
CREATE TABLE `customer_emails` (
  `customer_id` INT NOT NULL,
  `name` VARCHAR(2147483647) NOT NULL,
  `emails` ARRAY<STRING>,
  `desired_email_count` INT,
  PRIMARY KEY (`customer_id`) NOT ENFORCED
)
WITH (
  'connector' = 'faker',
  'rows-per-second' = '10',
  'fields.customer_id.expression' = '#{Number.numberBetween ''3000'',''3250''}',
  'fields.name.expression' = '#{Name.fullName}',
  'fields.emails.expression' = '#{Internet.emailAddress}',
  'fields.emails.length' = '3',
  'fields.desired_email_count.expression' = '#{Number.numberBetween ''0'',''3''}'
);
```

Create a second table with a single `emails` field containing the email addresses as a JSON array.
A variable number of emails is retained, between zero and three:
```sql
CREATE TABLE customer_emails_json (
    PRIMARY KEY (`customer_id`) NOT ENFORCED
)
AS SELECT
    customer_id,
    name,
    CASE
        WHEN desired_email_count = 0 THEN '[]'
        ELSE JSON_QUERY(CAST(JSON_ARRAY(ARRAY_SLICE(emails, 1, desired_email_count)) AS STRING),'$[0]')
        END AS emails
FROM customer_emails;
```

### Test the UDTF to normalize emails

```sql
SELECT
    customer_id,
    name,
    -- fields returned by the UDTF
    email_index,
    email
FROM customer_emails_json
LEFT JOIN LATERAL TABLE(normalize_json_emails(emails, TRUE)) ON TRUE
```

This will generate one row for each customer's email.
If a customer has no email, a single record is emitted with `NULL` values for `email_index` and `email`.

> ℹ️ the UDF also implements simple error handling, similar to [JsonAddressToRow](JsonAddressToRow.md).
> See [JsonAddressToRow: Testing error handling](JsonAddressToRow.md#testing-error-handling) for details.
