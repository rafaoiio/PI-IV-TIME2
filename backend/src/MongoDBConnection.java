import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

import java.io.IOException;
import java.io.Reader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Properties;

public class MongoDBConnection {

    public static MongoClient conectar() {

        // Localiza o arquivo de configuração.
        // Funciona executando pela pasta backend
        // ou pela raiz do projeto.

        Path caminho = Path.of("config.properties");

        if (!Files.isRegularFile(caminho)) {

            caminho = Path.of(
                    "backend",
                    "config.properties");

        }

        if (!Files.isRegularFile(caminho)) {

            throw new IllegalStateException(
                    "Arquivo config.properties não encontrado.");

        }

        // Lê as propriedades do arquivo.

        Properties propriedades = new Properties();

        try (
                Reader leitor = Files.newBufferedReader(
                        caminho,
                        StandardCharsets.UTF_8)) {

            propriedades.load(leitor);

        } catch (IOException erro) {

            throw new IllegalStateException(
                    "Erro ao ler config.properties.",
                    erro);

        }

        // Recupera a URI do MongoDB.

        String uri = propriedades.getProperty(
                "MONGO_URI");

        if (uri == null || uri.isBlank()) {

            throw new IllegalStateException(
                    "A propriedade MONGO_URI não foi configurada.");

        }

        // Cria o cliente MongoDB.
        // A conexão será verificada quando
        // executarmos uma operação no banco.

        return MongoClients.create(uri.trim());

    }

}
