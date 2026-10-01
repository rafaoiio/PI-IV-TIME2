# Sistema de Gestão e Redução de Desperdício de Alimentos

## Descrição

Projeto acadêmico de Engenharia de Software da PUC-Campinas para auxiliar estabelecimentos do setor alimentício no controle de estoque, acompanhamento de validades e redução do desperdício de alimentos.

O sistema está em desenvolvimento. Estão previstos cadastro e login de usuários, gerenciamento de produtos, registro e histórico de desperdícios, dashboard e relatórios.

## Tecnologias

- **Backend:** Java puro, com `com.sun.net.httpserver.HttpServer`.
- **Frontend:** HTML, Tailwind CSS e JavaScript.
- **Ferramentas de CSS:** Node.js e npm.
- **Banco de dados:** MongoDB, com integração futura.

## Como configurar e executar o projeto

### Pré-requisitos

Antes de começar, instale:

- JDK 17 ou superior, com `java` e `javac` disponíveis no terminal.
- Node.js 20 ou superior e npm.

Confira as instalações:

```sh
java -version
javac -version
node --version
npm --version
```

> No Windows, se o PowerShell bloquear o comando `npm`, utilize `npm.cmd` no lugar dele nos comandos abaixo.

### 1. Acesse a pasta do projeto

Depois de baixar ou clonar o repositório da equipe, abra o terminal e entre na pasta:

```sh
cd sistema-gestao-desperdicio-alimentos
```

A raiz do projeto é a pasta que contém `package.json`, `backend/`, `public/` e `views/`. Execute todos os próximos comandos nela.

### 2. Instale as dependências

```sh
npm ci
```

Esse comando instala as dependências do Tailwind nas versões registradas em `package-lock.json`.

### 3. Gere o CSS

```sh
npm run build
```

O comando gera `public/css/style.css` a partir de `public/css/input.css` e das classes utilizadas nas páginas.

### 4. Compile o backend

```sh
javac -encoding UTF-8 -d backend/bin backend/src/Main.java
```

Os arquivos compilados serão gerados em `backend/bin/`.

### 5. Inicie o servidor

```sh
java -cp backend/bin Main
```

Mantenha o terminal aberto. A mensagem esperada é:

```text
Servidor iniciado em http://localhost:8080/
```

### 6. Acesse pelo navegador

Abra [http://localhost:8080/](http://localhost:8080/).

> **Estado atual:** o servidor inicia, mas retorna HTTP 404 porque `Main.java` ainda não registra as rotas. A exibição das páginas, dos arquivos públicos e dos partials depende da implementação dos controllers.

Para encerrar o servidor, pressione `Ctrl+C` no terminal. Caso a porta 8080 esteja ocupada, encerre a instância anterior antes de iniciar outra.

## Durante o desenvolvimento

Para atualizar o CSS automaticamente ao editar as páginas e os estilos, abra **outro terminal na raiz** e execute:

```sh
npm run dev
```

Esse comando acompanha as alterações do CSS. O servidor Java continua sendo executado pelo comando da etapa 5.

- Edite os estilos em `public/css/input.css` e as classes nos arquivos HTML. O arquivo `style.css` é gerado automaticamente.
- Após alterar o código Java, encerre o servidor, compile e execute novamente.
- Recarregue o navegador para visualizar as alterações quando as rotas estiverem implementadas.

## Integração com MongoDB

A integração com o MongoDB será feita em uma etapa futura. **Não é necessário instalar ou configurar o banco para iniciar a versão atual.**

Quando a conexão estiver implementada, esta seção será atualizada com o passo a passo para:

1. Preparar o banco de dados.
2. Instalar o driver Java e configurar a conexão.
3. Definir as variáveis de ambiente necessárias.
4. Verificar a conexão e a persistência dos dados.

## Cronograma

O período previsto de desenvolvimento é de **29/09/2026 a 23/11/2026**, com etapas de estruturação, construção das telas, implementação do backend, integração, testes e entrega final.

## Versões e atualizações

Cada versão corresponde a uma etapa do cronograma concluída. A tag **`1.0.0`** ficará reservada para a entrega final do projeto.

| Versão / tag prevista | Etapa                              | Entrega da versão                                                                           | Prazo previsto (2026) | Status       | Data de conclusão |
| --------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------- | --------------------- | ------------ | ----------------- |
| `0.1.0`               | Estrutura inicial e banco de dados | Estrutura do frontend e backend, repositório, modelagem e conexão inicial com MongoDB.      | 05/10                 | Em andamento | —                 |
| `0.2.0`               | Frontend                           | Telas dos módulos previstas no cronograma concluídas.                                       | 12/10                 | Planejada    | —                 |
| `0.3.0`               | Base do backend                    | Rotas principais dos módulos funcionando.                                                   | 19/10                 | Planejada    | —                 |
| `0.4.0`               | Regras de negócio                  | Validações, regras de estoque e validade, baixa por desperdício e cálculos dos indicadores. | 26/10                 | Planejada    | —                 |
| `0.5.0`               | Integração frontend e backend      | Telas consumindo as APIs e realizando operações com dados reais.                            | 02/11                 | Planejada    | —                 |
| `0.6.0`               | Integração geral                   | Fluxos de usuários, produtos, estoque, vencimentos, desperdícios e indicadores integrados.  | 09/11                 | Planejada    | —                 |
| `0.7.0`               | Testes e correções                 | Módulos testados, revisão entre integrantes e correção dos problemas encontrados.           | 16/11                 | Planejada    | —                 |
| `1.0.0`               | Entrega final                      | MVP concluído, revisão final aprovada e documentação atualizada.                            | 23/11                 | Planejada    | —                 |

## Equipe

| Integrante                 | Responsabilidade prevista                                  |
| -------------------------- | ---------------------------------------------------------- |
| Laura Cristine Soares      | Produtos próximos do vencimento e registro de desperdício. |
| Livia Carvalho Lucizano    | Cadastro, edição e gerenciamento de produtos e estoque.    |
| Lucas David de Sousa       | Cadastro de usuários, perfil e configurações.              |
| Miriã Nascimento dos Anjos | Login, autenticação e histórico de desperdícios.           |
| Rafael Gaudencio Dias      | Dashboard e relatórios.                                    |

## Licença

Projeto acadêmico desenvolvido para a disciplina de Projeto Integrador 4 da PUC-Campinas.
