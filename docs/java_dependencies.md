## Java Dependencies in UDFs

When you build your own Java UDF project, there are some important aspects to consider.

Look at the [POM file](../pom.xml) of this project as an example to expand and adjust for your UDFs.


### Java version


The Java target version is set to 17. Confluent Cloud currently supports Java 11 and 17.
You can compile the project with a JDK newer than 17, but the build target is set to 17 by the POM.
If you have a JDK 11 you can change `target.java.version` to `11` to avoid compilation errors.

### Flink dependencies

Flink dependencies, such as `org.apache.flink:flink-table-api-java` and Log4j have scope `provided` so they are not included
in the uber-jar. It's important not to include any Flink dependency because it may cause conflicts with what's provided
by the Confluent Cloud runtime.

At the time of writing, the Flink API version supported for Confluent Cloud UDFs is `2.1.0`.

### Packaging dependencies

Any dependency which is not provided by the runtime must be packaged with the UDF artifact.

As you can see in the [POM file](../pom.xml), `maven-shade-plugin` is used to build an *uber-jar* including all required
dependencies.

### Avoiding dependency clashes

Some attention is required to avoid clashes with libraries provided by the runtime.

#### Flink and Log4j dependencies 

Flink dependencies, such as `org.apache.flink:flink-table-api-java`, and Log4j dependencies must have `provided` or `test` 
scope, to ensure they are excluded from the uber-jar.

#### Transitive dependencies

If your UDF uses any library also included as a transitive dependency from the Flink libraries, you should use the version
provided by Flink rather than adding your own dependency to the POM.
Failing to do this may cause errors when the UDF is executed, even if you tested your code locally with unit tests.

Examples are `org.apache.commons:commons-lang3` and `org.apache.commons:commons-text`.

You can see the transitive provided dependencies in the Maven dependency tree:

```shell
mvn dependency:tree -Dverbose | grep "provided"
```

#### Shaded dependencies

The Flink runtime includes a number of dependencies as *shaded*.  

Shading dependencies means remapping the included classes and all references to a different package.
For example, Flink includes Jackson2 with classes remapped from `com.fasterxml.jackson.*`
to `org.apache.flink.shaded.jackson2.com.fasterxml.jackson.*`.

Shaded dependencies do not cause conflicts.

If your UDF code needs any of these libraries, you have the option of including it as a dependency or using the one shaded in
Flink.

> ⚠️ When you add `import` in your code, pay attention to whether you are importing the shaded class or the class you included explicitly.

