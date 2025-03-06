# Highly Dependable Systems Project

## Compile

```bash
maven clean compile
```

## Start the Leader

```bash
mvn exec:java -Dexec.mainClass="com.example.Leader"  
```

## Start the members

```bash
mvn exec:java -Dexec.mainClass="com.example.Member"  -Dexec.args='<member_name>'
```

## Start the client

```bash
mvn exec:java -Dexec.mainClass='com.example.ClientLibrary
```
