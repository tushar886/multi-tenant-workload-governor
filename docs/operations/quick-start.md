# Quick Start

## Prerequisites

- JDK 21
- Maven 3.9+

## Run tests

```bash
mvn clean test
```

## Build

```bash
mvn package
```

## Run the example

Compile project sources and the example:

```bash
mkdir -p target/example-classes
javac --release 21 -cp target/classes -d target/example-classes examples/QuickStart.java
java -cp target/classes:target/example-classes QuickStart
```

## API lifecycle

```java
SubmissionDecision decision = governor.submit(request);
Optional<DispatchCandidate> candidate = governor.dispatchNext();
governor.complete(tenantId, workloadId, candidate.reservationVersion());
```

The real executor adapter must submit outside the governor lock and reconcile external state asynchronously. Do not put a Spark or Kubernetes network call inside `dispatchNext()`.
