// Código feito por Lucas David de Sousa RA: 25895152

package service;

import com.sun.net.httpserver.HttpExchange;

import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

import static com.mongodb.client.model.Filters.*;


/**
 * Serviço responsável por validar a sessão
 * e identificar o usuário autenticado.
 *
 * Pode ser utilizado por diferentes controllers
 * sem duplicar a lógica de autenticação.
 */
public class SessaoService {

    private final MongoCollection<Document> usuarios;
    private final MongoCollection<Document> sessoes;


    // Construtor

    public SessaoService(
        MongoCollection<Document> usuarios,
        MongoCollection<Document> sessoes
    ) {

        this.usuarios = usuarios;
        this.sessoes = sessoes;

    }


    // Extrair token do header

    public static String extrairToken(
        HttpExchange requisicao
    ) {

        String autorizacao = requisicao
            .getRequestHeaders()
            .getFirst("Authorization");

        if (
            autorizacao == null ||
            !autorizacao.startsWith("Bearer ")
        ) {

            return null;

        }

        String token = autorizacao
            .substring(7)
            .trim();

        if (
            token.isEmpty() ||
            token.length() > 200
        ) {

            return null;

        }

        return token;

    }


    // Autenticar usuário

    public Document autenticar(
        HttpExchange requisicao
    ) {

        // Recupera o token enviado pelo frontend
        String token = extrairToken(requisicao);

        if (token == null) {

            return null;

        }


        // Consulta a sessão no MongoDB
        // A sessão precisa existir e não estar expirada

        Document sessao = sessoes.find(
            and(
                eq("token", token),
                gt("expiraEm", new Date())
            )
        ).first();


        // Token inexistente ou sessão expirada
        if (sessao == null) {

            return null;

        }


        // Obtém o ID do usuário associado à sessão
        ObjectId idUsuario =
            sessao.getObjectId("usuarioId");

        if (idUsuario == null) {

            return null;

        }


        // Consulta o usuário real no MongoDB
        Document usuario = usuarios.find(
            eq("_id", idUsuario)
        ).first();


        // Usuário não encontrado
        if (usuario == null) {

            return null;

        }


        // Não permite acesso a usuários desativados
        if (
            Boolean.FALSE.equals(
                usuario.getBoolean("ativo")
            )
        ) {

            return null;

        }


        // A autenticação foi realizada com sucesso
        return usuario;

    }
    

    // Encerrar sessões do usuário

    public void encerrarSessoesDoUsuario(
        ObjectId idUsuario
    ) {

        sessoes.deleteMany(
            eq("usuarioId", idUsuario)
        );

    }

}