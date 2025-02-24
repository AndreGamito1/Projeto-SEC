# Highly Dependable Systems Project

## Compile:

```bash
javac -d ./output *.java
```

## To run:

```bash
#Generate the AES key
java -cp ./output AESKeyGenerator w keys/aes_key.key
```

#### Server

```bash
# Generate server rsa keys
java -cp ./output RSAKeyGenerator w keys/server_private.key keys/server_public.key 
java -cp ./output Server
```

#### Client

```bash
# Generate client rsa keys
java -cp ./output RSAKeyGenerator w keys/client_private.key keys/client_public.key 
java -cp ./output Client
```
