## Usage of UDTF examples

> ⚠️ Make sure in your SQL Workspace you select Catalog and Database corresponding to your environment and cluster.
> Do not select `examples` and `marketplace`.

### JsonAddressToRow

User Defined Table Function (UDTF) source code: 
[JsonAddressToRow](../src/main/java/io/confluent/flink/examples/udf/table/JsonAddressToRow.java)

This User Defined Table Function (UDTF) unnests a STRING field containing a JSON representation of the address in this form:

```json
{ 
  "street" : "91839 Satterfield Wall", 
  "postcode": "05420", 
  "city" : "Wunschtown"
}
```

...returning a `ROW` containing three separate fields `street`, `postcode`, and `city`.

The function error handling, when a malformed JSON is encountered, depends on the second parameter (`failOnError`) passed
to the function:
- `failOnError` = `TRUE`: the UDTF fails when a malformed JSON is encountered.
- `failOnError` = `FALSE`: the UDTF logs the parsing failure, and returns nothing.

Note that, if a valid JSON is encountered, but the `street`, `postcode`, or `city` fields are not present, this is not handled
as a parsing failure but `NULL` is returned for the missing fields.

#### Register the User Defined Table Function (UDTF)

Register the function. Replace `<artifact-id>` with the ID of the JAR artifact you uploaded.
The artifact ID is a string starting with `cfa-` like `cfa-abc1234`.

```sql
CREATE FUNCTION `unnest_json_address`
  AS 'io.confluent.flink.examples.udf.table.JsonAddressToRow'
  USING JAR 'confluent-artifact://<artifact-id>'
```

Verify registration:
```sql
DESCRIBE FUNCTION EXTENDED `unnest_json_address`
```

#### Prepare the input data

To test the function, we need to create a new table with the JSON representation of the address:

```sql
CREATE TABLE customers_json (
  PRIMARY KEY(`customer_id`) NOT ENFORCED
)
WITH (
  'value.format' = 'json-registry'
)
AS SELECT 
  customer_id,
  `name`,
  CONCAT('{ "street" : "', address, '", "postcode" : "', postcode, '", "city" : "', city, '"}') AS full_address,
  email
FROM `examples`.`marketplace`.`customers`
```

Now we can test the UDTF.

#### Test the UDTF to unnest the JSON address

Let's test the UDTF setting `failOnError` = `TRUE`:
```sql
SELECT
    `customer_id`,
    `name`,
    `email`,
    -- the fields below are returned by the UDTF
    `street`,
    `postcode`,
    `city`
FROM customers_json
LEFT JOIN LATERAL TABLE(unnest_json_address(full_address, TRUE)) ON TRUE
```

#### Testing error handling

Inject a record with a malformed JSON:

```sql
INSERT INTO customers_json VALUES (1234, 'my name', '{ this-is-malformed }', 'some@email.com')
```

When the malformed record reaches the query (it may take a few seconds), the query will fail, with an exception like:
*"UDF invocation error: exception raised in the user function code"*.

Let's re-run the same query, but passing `FALSE` as the second parameter (`failOnError`).

```sql
SELECT
    `customer_id`,
    `name`,
    `email`,
    -- fields returned by the UDTF
    `street`,
    `postcode`,
    `city`
FROM customers_json
LEFT JOIN LATERAL TABLE(unnest_json_address(full_address, FALSE)) ON TRUE
```

Let's inject another malformed record:

```sql
INSERT INTO customers_json VALUES (4321, 'my name', '{ this-is-malformed }', 'some@email.com')
```

To see the record, filter the output of the query by `customer_id` = `4321`.

When the malformed record is processed, you should see a record with `street`, `postcode`, and `city` all `NULL`,
and the query will continue running.

> ℹ️ Using `CROSS JOIN LATERAL TABLE` instead of `LEFT JOIN LATERAL TABLE`, no record would have been emitted
> for the malformed record.

If you check the statement logging, in the Compute Pool *Logging* tab, you should see a record from Source `Function` at
Log level `WARN`.

> ℹ️ This error handling allows you to log on malformed records, but does not send the offending record to a Dead Letter Queue.
> This feature is not yet available.

---


### NormalizeJsonArray

User Defined Table Function (UDTF) source code:
[NormalizeJsonArray](../src/main/java/io/confluent/flink/examples/udf/table/NormalizeJsonArray.java)

This UDTF demonstrates how you can normalize a JSON payload emitting multiple records for each nested element.

In this example, the UDTF expands a field containing a simple JSON array, which is roughly what the Flink system function `UNNEST`
does. The implementation can be easily expanded with a more complex logic, for example looking for specific elements or fields to extract. 

#### Register the User Defined Table Function (UDTF)

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

#### Prepare input data

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

#### Test the UDTF to normalize emails

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

> ℹ️ the UDF also implements simple error handling, similar to `JsonAddressToRow`.
> See [Testing error handling](#testing-error-handling).