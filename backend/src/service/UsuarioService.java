// Código feito por Lucas David de Sousa RA: 25895152

package service;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;

public class UsuarioService {

    private final MongoCollection<Document> usuarios;

    public UsuarioService(
        MongoCollection<Document> usuarios
    ) {
        this.usuarios = usuarios;
    }

    public Document cadastrarUsuario(Document dados) {

        if (dados == null) {
            throw new IllegalArgumentException(
                "Dados do cadastro inválidos."
            );
        }

        // Recuperar e normalizar os dados
        String nome = obterTexto(dados, "nome")
            .trim()
            .replaceAll("\\s+", " ");

        String email = obterTexto(dados, "email")
            .trim()
            .toLowerCase(Locale.ROOT);

        String celular = obterTexto(dados, "celular")
            .replaceAll("\\D", "");

        String senha = obterTexto(dados, "senha");

        // Remover o código do Brasil, se presente
        if (
            celular.length() == 13 &&
            celular.startsWith("55")
        ) {
            celular = celular.substring(2);
        }

        // Validar nome completo
        if (
            nome.length() < 3 ||
            nome.length() > 100 ||
            nome.split(" ").length < 2
        ) {
            throw new IllegalArgumentException(
                "Informe um nome completo válido."
            );
        }

        // Validar e-mail
        if (
            email.length() > 150 ||
            !email.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
            )
        ) {
            throw new IllegalArgumentException(
                "Informe um e-mail válido."
            );
        }

        // Validar celular com DDD
        if (!celular.matches("\\d{11}")) {
            throw new IllegalArgumentException(
                "Informe um celular válido com DDD."
            );
        }

        // Validar senha
        int tamanhoSenhaBytes = senha
            .getBytes(StandardCharsets.UTF_8)
            .length;

        if (
            senha.length() < 8 ||
            senha.length() > 50 ||
            tamanhoSenhaBytes > 72
        ) {
            throw new IllegalArgumentException(
                "A senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
            );
        }

        // Gerar hash BCrypt da senha
        String senhaHash = BCrypt.hashpw(
            senha,
            BCrypt.gensalt(12)
        );

        // Preparar os dados para o MongoDB
        Document usuario = new Document()
            .append("nome", nome)
            .append("email", email)
            .append("celular", celular)
            .append("senhaHash", senhaHash)
            .append("criadoEm", new Date());

        // Cadastrar usuário na collection users
        usuarios.insertOne(usuario);

        // Retornar os dados públicos do cadastro
        return new Document(
            "mensagem",
            "Usuário cadastrado com sucesso!"
        ).append(
            "id",
            usuario.getObjectId("_id").toHexString()
        );
    }

    // Recuperar um campo de texto com segurança
    private static String obterTexto(
        Document dados,
        String campo
    ) {
        Object valor = dados.get(campo);

        return valor instanceof String
            ? (String) valor
            : "";
    }
}