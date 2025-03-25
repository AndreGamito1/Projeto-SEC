# Highly Dependable Systems Project - Stage 1

- André Pereira -  ist1103082
- André Gamito  -  ist1104167
- Miguel Bibi   -  ist1102737

## TODO
FALTA:
- CLIENTLIBRARY
- TIMESTAMPS
- ~~PROVAVELMENTE EPOCHSTATE (MAIS FODIDO #GG)~~
- ~~LEADER QUEUE MUTIO IMPORTANTE SE N QUEBRA TUDO (MEMBERS MANDAM DECIDE E LEADER ESPERA POR QUORUM DE DECIDES OU DE ABORTS)~~
- O TIMEOUT DA ESPERA DE STATES ESTÁ A FUNCIONAR?
- ~~USAR SÓ UM WORKING~~

### Requesitos de Design  

- Implementar uma criptomoeda nativa, "DepCoin"
- Um Cliente pode realizar transferências de "DepCoin" entre um par de contas, desde que possua a chave privada correspondente à conta de crédito (a de onde o dinheiro é retirado)
- Todas as contas e saldos devem satisfazer as seguintes garantias de confiabilidade e segurança:
    - O saldo de cada conta deve ser não negativo
    - O estado das contas não pode ser modificado por usuários nao autorizados.
    - O sistema deve garantir a não repudiação de todas as operações emitidas em uma conta.
    
- O sistema deve começar com um bloco pré-cosntruído( bloco gênesis ou bloco 0 ) que inclui 2 contratos inteligente implantados, onde ambos os contratos devem ser escritos em *Solidity* e compilados para bytecode EVM
    - Um contrato que implementa um token fungível ERC-20. O token ERC-20 será chamado "IST Coin", seu símbolo será "IST", terá 2 casas decimais e um fornecimento total de 100 milhões de unidades. Ao realizar uma transferência ou transferir de, o contrato ERC-20 chamará o contrato de controle de acesso para verificar se o endereço da conta cliente está autorizado a transferir.

    - Um contrato que implementa uma lista de controle de acesso baseada em blacklist. Este contrato inteligente deve fornecer a interface abaixo. Observe que apenas partes autorizadas devem poder modificar a blacklist.

- Além disso, os estudantes devem criar um arquivo de gênesis, por exemplo, em formato JSON, que represente o estado do mundo do primeiro bloco (ou seja, bloco 0) e inclua uma inicialização com todos os endereços das contas, seus saldos e os endereços dos contratos inteligentes, incluindo seus bytecodes e os valores de armazenamento de chaves iniciais. Um exemplo poderia ser assim:

```JSON 
{
“block_hash”: <HASH_DA_LISTA_DE_TRANSACOES>,
“previous_block_hash”: null,  
“transactions”: [] 
“state”: { 
  “<Endereco_da_Conta_EOA>”: { 
   “balance”: “100000” 
  },
  “<Endereco_do_Contrato>”: { 
   “balance”: 0, 
   “code”: “0x60016002…” 
   “storage”: { 
     “key1”: “value1”, 
     “key2”: “value2”, 
     … 
   } 
  } 
  … 
}
}
```

As transações devem ser agrupadas em blocos e cada bloco deve apontar para seu bloco anterior e representar o estado do mundo após a execução das transações incluídas no bloco, em comparação com o bloco anterior. Os blocos devem ser persistidos usando o mesmo formato que o bloco gênesis.

### Requesitos de Implementação

- O projeto deve ser implementado em Java usando o Hyperledger Besu para a execução de contratos inteligentes em Solidity.
- O modelo de conta deve ser baseado no modelo do Ethereum, significando que existem dois tipos de contas: 
    - **Externally Owned Accounts** (EOA, ou seja, pares de chave pública/privada) 
    - **Contract Accounts** (contratos inteligentes). 
- Cada conta (independente do tipo) será associada a um endereço e terá um saldo (ou seja, a quantidade de criptomoeda nativa possuída pela conta). 
- Contas do tipo **Contract Account** também têm código (bytecode EVM) e armazenamento (chave-valor) associados.

- O conceito de transações não precisa ser equivalente ao modelo do Ethereum. **Você é livre para projetar seu próprio objeto de transação**, no entanto, ele deve permitir a execução de funções de contratos inteligentes e a transferência de criptomoeda nativa.

### Passos de Implementação

- **Step 1**: Garantir que todos os requisitos da primeira etapa sejam atendidos.
- **Step 2**: Implementar os contratos inteligentes em Solidity e testar sua execução usando o código base EVM do Hyperledger Besu fornecido no segundo trabalho de laboratório (ou seja, lab2).
- **Step 3**: Implementar o conceito de bloco gênesis, transações, execução de transações (por exemplo, execução de contratos inteligentes), bem como a adição e persistência de blocos.
- **Step 4**: Conectar o algoritmo de consenso desenvolvido na primeira etapa aos componentes desenvolvidos na primeira etapa. O consenso deve ser responsável por decidir a ordem das transações a serem executadas e adicionadas ao blockchain.
- **Step 5**: Implementar um conjunto completo de testes que demonstrem que a implementação é robusta contra comportamentos bizantinos.





## Demo Quick Start

Due to a late discovery of architectural flaws, we were forced to make significant changes to our architecture, which left us without enough time to complete the full implementation.

You can start the demo using either the provided PowerShell script (Windows) or Bash script (Linux/macOS).

### Windows (PowerShell)

```powershell
.\Start-BlockchainSystem.ps1
```

### Linux/macOS (Bash)

```bash
chmod +x run.sh

./run.sh
```

## Manual Startup

To start each component manually:

### 1. Prepare Key Directories

```bash
# Create/clean directories for keys
mkdir -p shared/priv_keys shared/pub_keys
rm -rf shared/priv_keys/* shared/pub_keys/*
```

## 2. Start Leader

Open a new terminal and run:

```bash
mvn exec:java -Dexec.mainClass=com.example.Leader
```

Wait a few seconds for the Leader to initialize.

## 3. Start Members

Open a new terminal for each member (1-4) and run:

```bash
# For Member 1
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member1

# For Member 2
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member2

# For Member 3
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member3

# For Member 4
mvn exec:java -Dexec.mainClass=com.example.Member -Dexec.args=member4

```

Wait a few seconds for all Members to initialize.

### 4. Start ClientLibrary

Open a new terminal and run:

```bash
# Start ClientLibrary with REST API on port 8080
mvn exec:java -Dexec.mainClass=com.example.ClientLibrary -Dexec.args=8080
```

### 5. Start Clients

Open a new terminal for each client and run:

```bash
# Start Client 1
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client1

# For additional clients, use a unique client ID
mvn exec:java -Dexec.mainClass=com.example.Client -Dexec.args=Client2
```

### System Requirements

- Java 8 or higher
- Maven
