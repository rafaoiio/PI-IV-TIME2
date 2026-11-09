// Código feito por Lucas David de Sousa RA: 25895152

package service;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Updates;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.mindrot.jbcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.NoSuchElementException;

import static com.mongodb.client.model.Filters.eq;

public class PerfilUsuarioService {

    private final MongoCollection<Document> usuarios;
    private final SessaoService sessaoService;

    // Construtor
    public PerfilUsuarioService(
        MongoCollection<Document> usuarios,
        SessaoService sessaoService
    ) {
        this.usuarios = usuarios;
        this.sessaoService = sessaoService;
    }

    // Atualizar e-mail ou celular do usuário
    public Document atualizarDados(
        Document usuario,
        Document dados
    ) {

        // Verificar os dados recebidos
        if (
            dados == null ||
            dados.size() != 1 ||
            !(
                dados.containsKey("email") ||
                dados.containsKey("celular")
            )
        ) {
            throw new IllegalArgumentException(
                "Envie somente email ou celular."
            );
        }

        // Identificar o campo que será atualizado
        String campo = dados.containsKey("email")
            ? "email"
            : "celular";

        Object valorRecebido = dados.get(campo);

        if (!(valorRecebido instanceof String)) {
            throw new IllegalArgumentException(
                "O campo informado deve ser texto."
            );
        }

        String valor;

        // Validar e-mail
        if ("email".equals(campo)) {

            valor = ((String) valorRecebido)
                .strip()
                .toLowerCase(Locale.ROOT);

            if (
                valor.length() > 150 ||
                !valor.matches(
                    "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
                )
            ) {
                throw new IllegalArgumentException(
                    "Informe um e-mail válido."
                );
            }

        } else {

            // Remover caracteres da formatação do celular
            valor = ((String) valorRecebido)
                .replaceAll("\\D", "");

            // Remover código do Brasil, se presente
            if (
                valor.length() == 13 &&
                valor.startsWith("55")
            ) {
                valor = valor.substring(2);
            }

            // Validar celular com DDD
            if (!valor.matches("\\d{11}")) {
                throw new IllegalArgumentException(
                    "Informe um celular válido com DDD."
                );
            }
        }

        // Identificar o usuário autenticado
        ObjectId idUsuario = usuario.getObjectId("_id");

        // Atualizar o campo no MongoDB
        usuarios.updateOne(
            eq("_id", idUsuario),
            Updates.set(campo, valor)
        );

        // Consultar os dados atualizados
        Document atualizado = usuarios.find(
            eq("_id", idUsuario)
        ).first();

        if (atualizado == null) {
            throw new NoSuchElementException(
                "Usuário não encontrado."
            );
        }

        // Retornar os dados atualizados
        return new Document(
            "mensagem",
            "Dados atualizados com sucesso!"
        ).append(
            "usuario",
            dadosPublicos(atualizado)
        );
    }

    // Alterar a senha do usuário
    public Document alterarSenha(
        Document usuario,
        Document dados
    ) {

        // Verificar os campos recebidos
        if (
            dados == null ||
            dados.size() != 2 ||
            !(dados.get("senhaAtual") instanceof String) ||
            !(dados.get("novaSenha") instanceof String)
        ) {
            throw new IllegalArgumentException(
                "Informe a senha atual e a nova senha."
            );
        }

        String senhaAtual = dados.getString("senhaAtual");
        String novaSenha = dados.getString("novaSenha");

        String senhaHash = usuario.getString("senhaHash");

        // Validar a senha atual com BCrypt
        if (
            senhaAtual.isEmpty() ||
            senhaHash == null ||
            !BCrypt.checkpw(senhaAtual, senhaHash)
        ) {
            throw new IllegalArgumentException(
                "Senha atual incorreta."
            );
        }

        // Validar o tamanho da nova senha
        int tamanhoSenhaBytes = novaSenha
            .getBytes(StandardCharsets.UTF_8)
            .length;

        if (
            novaSenha.length() < 8 ||
            novaSenha.length() > 50 ||
            tamanhoSenhaBytes > 72
        ) {
            throw new IllegalArgumentException(
                "A nova senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
            );
        }

        // Impedir a reutilização da senha atual
        if (BCrypt.checkpw(novaSenha, senhaHash)) {
            throw new IllegalArgumentException(
                "A nova senha deve ser diferente da atual."
            );
        }

        // Identificar o usuário autenticado
        ObjectId idUsuario = usuario.getObjectId("_id");

        // Gerar o novo hash BCrypt
        String novoHash = BCrypt.hashpw(
            novaSenha,
            BCrypt.gensalt(12)
        );

        // Atualizar o hash no MongoDB
        usuarios.updateOne(
            eq("_id", idUsuario),
            Updates.set("senhaHash", novoHash)
        );

        // Encerrar todas as sessões do usuário
        sessaoService.encerrarSessoesDoUsuario(
            idUsuario
        );

        // Retornar a confirmação da alteração
        return new Document(
            "mensagem",
            "Senha alterada com sucesso! Faça login novamente."
        );
    }

    // Preparar os dados públicos do usuário
    private static Document dadosPublicos(
        Document usuario
    ) {

        return new Document(
            "id",
            usuario.getObjectId("_id").toHexString()
        )
            .append(
                "nome",
                usuario.getString("nome")
            )
            .append(
                "email",
                usuario.getString("email")
            )
            .append(
                "celular",
                usuario.getString("celular")
            );
    }
}