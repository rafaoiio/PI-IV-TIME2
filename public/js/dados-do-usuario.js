// Código feito por Lucas David de Sousa RA: 25895152

document.addEventListener("DOMContentLoaded", () => {

  const API_URL = "http://127.0.0.1:8080";

  const $ = (id) => document.getElementById(id);


  // Elementos gerais

  const statusPerfil = $("statusPerfil");

  const nomeUsuario = $("nomeUsuario");
  const emailUsuario = $("emailUsuario");
  const telefoneUsuario = $("telefoneUsuario");
  const cargoUsuario = $("cargoUsuario");


  // Elementos do e-mail

  const visualizacaoEmail = $("visualizacaoEmail");
  const edicaoEmail = $("edicaoEmail");

  const inputEmailUsuario = $("inputEmailUsuario");
  const erroEmailUsuario = $("erroEmailUsuario");

  const btnEditarEmail = $("btnEditarEmail");
  const btnSalvarEmail = $("btnSalvarEmail");
  const btnCancelarEmail = $("btnCancelarEmail");


  // Elementos do telefone

  const visualizacaoTelefone = $("visualizacaoTelefone");
  const edicaoTelefone = $("edicaoTelefone");

  const inputTelefoneUsuario = $("inputTelefoneUsuario");
  const erroTelefoneUsuario = $("erroTelefoneUsuario");

  const btnEditarTelefone = $("btnEditarTelefone");
  const btnSalvarTelefone = $("btnSalvarTelefone");
  const btnCancelarTelefone = $("btnCancelarTelefone");


  // Elementos da senha

  const modalAlterarSenha = $("modalAlterarSenha");

  const senhaAtual = $("senhaAtual");
  const novaSenha = $("novaSenha");
  const confirmarNovaSenha = $("confirmarNovaSenha");

  const erroSenhaUsuario = $("erroSenhaUsuario");

  const btnAlterarSenha = $("btnAlterarSenha");
  const btnSalvarSenha = $("btnSalvarSenha");
  const btnCancelarSenha = $("btnCancelarSenha");

  const btnMostrarNovaSenha = $("btnMostrarNovaSenha");

  const btnMostrarConfirmacaoSenha =
    $("btnMostrarConfirmacaoSenha");


  // Elementos da foto

  const fotoUsuario = $("fotoUsuario");
  const fotoPlaceholder = $("fotoPlaceholder");

  const btnAlterarFoto = $("btnAlterarFoto");
  const inputFotoUsuario = $("inputFotoUsuario");

  const erroFotoUsuario = $("erroFotoUsuario");


  // Variáveis

  let usuario = null;

  let salvando = false;


  // Comunicação com a API

  async function chamarAPI(caminho, opcoes = {}) {

    // Obtém o token criado durante o login
    const token = sessionStorage.getItem("token");

    if (!token) {

      const erro = new Error(
        "Você precisa fazer login para acessar seus dados."
      );

      erro.status = 401;

      throw erro;
    }

    // Envia o token para autenticação no Java
    const resposta = await fetch(
      `${API_URL}${caminho}`,
      {
        ...opcoes,

        headers: {
          "Content-Type": "application/json",

          Authorization: `Bearer ${token}`,

          ...(opcoes.headers || {})
        }
      }
    );

    const dados = await resposta.json().catch(
      () => ({})
    );

    if (!resposta.ok) {

      const erro = new Error(
        dados.mensagem ||
        "Não foi possível concluir a operação."
      );

      erro.status = resposta.status;

      throw erro;
    }

    return dados;
  }


  // Mensagens gerais

  function mostrarStatus(
    mensagem,
    tipo = "normal"
  ) {

    statusPerfil.textContent = mensagem;

    statusPerfil.classList.remove(
      "hidden",
      "text-danger",
      "text-primary-darker"
    );

    if (tipo === "erro") {

      statusPerfil.classList.add(
        "text-danger"
      );

    } else if (tipo === "sucesso") {

      statusPerfil.classList.add(
        "text-primary-darker"
      );
    }
  }


  function limparStatus() {

    statusPerfil.textContent = "";

    statusPerfil.classList.add("hidden");
  }


  // Habilitar e desabilitar botões de edição

  function habilitarEdicao(habilitado) {

    [
      btnEditarEmail,
      btnEditarTelefone,
      btnAlterarSenha,
      btnAlterarFoto
    ].forEach((botao) => {

      botao.disabled = !habilitado;

    });
  }


  // Sessão expirada ou inexistente

  function sessaoExpirada() {

    // Remove os dados da sessão atual
    sessionStorage.removeItem("token");

    sessionStorage.removeItem("usuario");

    usuario = null;

    habilitarEdicao(false);

    fecharEdicaoEmail();

    fecharEdicaoTelefone();

    mostrarStatus(
      "Sua sessão expirou ou não existe. Faça login novamente.",
      "erro"
    );
  }


  // Tratamento de erros

  function mensagemErro(erro) {

    if (erro.status === 401) {

      sessaoExpirada();

      return "Sessão expirada. Faça login novamente.";
    }

    if (erro instanceof TypeError) {

      return (
        "Falha de conexão com a API. " +
        "Confira se o backend Java está ligado."
      );
    }

    return (
      erro.message ||
      "Ocorreu um erro inesperado."
    );
  }


  // Atualizar dados na tela

  function atualizarTela() {

    if (!usuario) return;

    nomeUsuario.textContent =
      usuario.nome || "Não informado";

    emailUsuario.textContent =
      usuario.email || "Não informado";

    telefoneUsuario.textContent =
      formatarTelefone(usuario.celular || "");

    // O cadastro atual não possui cargo
    cargoUsuario.textContent =
      usuario.cargo || "Não informado";

    atualizarFoto();
  }


  // Consultar perfil no MongoDB

  async function carregarPerfil() {

    habilitarEdicao(false);

    mostrarStatus(
      "Carregando seus dados..."
    );

    try {

      // GET /api/usuario

      usuario = await chamarAPI(
        "/api/usuario"
      );

      atualizarTela();

      // Atualiza o usuário da sessão local
      sessionStorage.setItem(
        "usuario",
        JSON.stringify(usuario)
      );

      habilitarEdicao(true);

      limparStatus();

    } catch (erro) {

      nomeUsuario.textContent =
        "Dados indisponíveis";

      emailUsuario.textContent = "—";

      telefoneUsuario.textContent = "—";

      cargoUsuario.textContent =
        "Não informado";

      mostrarStatus(
        mensagemErro(erro),
        "erro"
      );
    }
  }


  // Atualizar um campo no MongoDB

  async function atualizarCampo(campo, valor) {

    // O backend identifica o usuário pelo token

    const resposta = await chamarAPI(
      "/api/perfil",
      {
        method: "PATCH",

        body: JSON.stringify({
          [campo]: valor
        })
      }
    );

    // Atualiza os dados após a confirmação
    usuario = resposta.usuario;

    sessionStorage.setItem(
      "usuario",
      JSON.stringify(usuario)
    );

    atualizarTela();
  }


  // Mensagens de validação

  function mostrarErro(
    elemento,
    input,
    texto
  ) {

    elemento.textContent = texto;

    elemento.classList.remove("hidden");

    input.setAttribute(
      "aria-invalid",
      "true"
    );
  }


  function removerErro(elemento, input) {

    elemento.textContent = "";

    elemento.classList.add("hidden");

    input.removeAttribute("aria-invalid");
  }


  // Editar e-mail

  function fecharEdicaoEmail() {

    removerErro(
      erroEmailUsuario,
      inputEmailUsuario
    );

    edicaoEmail.classList.add("hidden");

    visualizacaoEmail.classList.remove(
      "hidden"
    );
  }


  btnEditarEmail.addEventListener(
    "click",
    () => {

      if (!usuario || salvando) return;

      fecharEdicaoTelefone();

      limparStatus();

      inputEmailUsuario.value =
        usuario.email || "";

      removerErro(
        erroEmailUsuario,
        inputEmailUsuario
      );

      visualizacaoEmail.classList.add(
        "hidden"
      );

      edicaoEmail.classList.remove(
        "hidden"
      );

      inputEmailUsuario.focus();

      inputEmailUsuario.select();
    }
  );


  btnCancelarEmail.addEventListener(
    "click",
    () => {

      if (!salvando) {
        fecharEdicaoEmail();
      }
    }
  );


  inputEmailUsuario.addEventListener(
    "input",
    () => {

      removerErro(
        erroEmailUsuario,
        inputEmailUsuario
      );
    }
  );


  inputEmailUsuario.addEventListener(
    "keydown",
    (evento) => {

      if (evento.key === "Enter") {

        evento.preventDefault();

        salvarEmail();

      } else if (
        evento.key === "Escape" &&
        !salvando
      ) {

        fecharEdicaoEmail();
      }
    }
  );


  btnSalvarEmail.addEventListener(
    "click",
    salvarEmail
  );


  // Salvar e-mail

  async function salvarEmail() {

    if (!usuario || salvando) return;

    const email = inputEmailUsuario.value
      .trim()
      .toLowerCase();

    // Validação no frontend
    if (
      !email ||
      email.length > 150 ||
      !inputEmailUsuario.checkValidity()
    ) {

      mostrarErro(
        erroEmailUsuario,
        inputEmailUsuario,
        "Digite um e-mail válido."
      );

      return;
    }

    // Não salva quando o e-mail não mudou
    if (email === usuario.email) {

      fecharEdicaoEmail();

      return;
    }

    salvando = true;

    btnSalvarEmail.disabled = true;

    btnSalvarEmail.textContent =
      "Salvando...";

    try {

      await atualizarCampo(
        "email",
        email
      );

      fecharEdicaoEmail();

      mostrarStatus(
        "E-mail atualizado com sucesso!",
        "sucesso"
      );

    } catch (erro) {

      if (erro.status === 401) {

        mensagemErro(erro);

      } else {

        mostrarErro(
          erroEmailUsuario,
          inputEmailUsuario,
          mensagemErro(erro)
        );
      }

    } finally {

      salvando = false;

      btnSalvarEmail.disabled = false;

      btnSalvarEmail.textContent = "Salvar";
    }
  }


  // Telefone: obter apenas os números

  function obterNumerosTelefone(valor) {

    let numeros = String(valor || "")
      .replace(/\D/g, "");

    // Remove o código do Brasil, se presente
    if (
      numeros.length === 13 &&
      numeros.startsWith("55")
    ) {

      numeros = numeros.substring(2);
    }

    return numeros;
  }


  // Telefone: aplicar máscara

  function aplicarMascaraTelefone(valor) {

    const numeros = obterNumerosTelefone(
      valor
    ).substring(0, 11);

    if (!numeros) return "";

    if (numeros.length <= 2) {

      return `(${numeros}`;
    }

    if (numeros.length <= 7) {

      return (
        `(${numeros.substring(0, 2)}) ` +
        numeros.substring(2)
      );
    }

    return (
      `(${numeros.substring(0, 2)}) ` +
      `${numeros.substring(2, 7)}-` +
      numeros.substring(7)
    );
  }


  // Telefone: formatar para exibição

  function formatarTelefone(valor) {

    const numeros = obterNumerosTelefone(valor);

    if (numeros.length !== 11) {

      return "Não informado";
    }

    return aplicarMascaraTelefone(numeros);
  }


  // Fechar edição do telefone

  function fecharEdicaoTelefone() {

    removerErro(
      erroTelefoneUsuario,
      inputTelefoneUsuario
    );

    edicaoTelefone.classList.add(
      "hidden"
    );

    visualizacaoTelefone.classList.remove(
      "hidden"
    );
  }


  // Abrir edição do telefone

  btnEditarTelefone.addEventListener(
    "click",
    () => {

      if (!usuario || salvando) return;

      fecharEdicaoEmail();

      limparStatus();

      inputTelefoneUsuario.value =
        formatarTelefone(usuario.celular);

      removerErro(
        erroTelefoneUsuario,
        inputTelefoneUsuario
      );

      visualizacaoTelefone.classList.add(
        "hidden"
      );

      edicaoTelefone.classList.remove(
        "hidden"
      );

      inputTelefoneUsuario.focus();

      inputTelefoneUsuario.select();
    }
  );


  btnCancelarTelefone.addEventListener(
    "click",
    () => {

      if (!salvando) {
        fecharEdicaoTelefone();
      }
    }
  );


  // Máscara durante a digitação

  inputTelefoneUsuario.addEventListener(
    "input",
    () => {

      inputTelefoneUsuario.value =
        aplicarMascaraTelefone(
          inputTelefoneUsuario.value
        );

      removerErro(
        erroTelefoneUsuario,
        inputTelefoneUsuario
      );
    }
  );


  inputTelefoneUsuario.addEventListener(
    "keydown",
    (evento) => {

      if (evento.key === "Enter") {

        evento.preventDefault();

        salvarTelefone();

      } else if (
        evento.key === "Escape" &&
        !salvando
      ) {

        fecharEdicaoTelefone();
      }
    }
  );


  btnSalvarTelefone.addEventListener(
    "click",
    salvarTelefone
  );


  // Salvar telefone

  async function salvarTelefone() {

    if (!usuario || salvando) return;

    const celular = obterNumerosTelefone(
      inputTelefoneUsuario.value
    );

    if (!/^\d{11}$/.test(celular)) {

      mostrarErro(
        erroTelefoneUsuario,
        inputTelefoneUsuario,
        "Digite um celular válido com DDD."
      );

      return;
    }

    // Não salva caso o número seja igual
    if (celular === usuario.celular) {

      fecharEdicaoTelefone();

      return;
    }

    salvando = true;

    btnSalvarTelefone.disabled = true;

    btnSalvarTelefone.textContent =
      "Salvando...";

    try {

      await atualizarCampo(
        "celular",
        celular
      );

      fecharEdicaoTelefone();

      mostrarStatus(
        "Celular atualizado com sucesso!",
        "sucesso"
      );

    } catch (erro) {

      if (erro.status === 401) {

        mensagemErro(erro);

      } else {

        mostrarErro(
          erroTelefoneUsuario,
          inputTelefoneUsuario,
          mensagemErro(erro)
        );
      }

    } finally {

      salvando = false;

      btnSalvarTelefone.disabled = false;

      btnSalvarTelefone.textContent =
        "Salvar";
    }
  }


  // ALteração de senha

  // Fechar modal de alteração de senha

  function fecharModalSenha() {

    senhaAtual.value = "";

    novaSenha.value = "";

    confirmarNovaSenha.value = "";

    novaSenha.type = "password";

    confirmarNovaSenha.type = "password";

    btnMostrarNovaSenha.setAttribute(
      "aria-label",
      "Mostrar senha"
    );

    btnMostrarConfirmacaoSenha.setAttribute(
      "aria-label",
      "Mostrar senha"
    );

    erroSenhaUsuario.textContent = "";

    erroSenhaUsuario.classList.add(
      "hidden"
    );

    modalAlterarSenha.classList.add(
      "hidden"
    );

    modalAlterarSenha.classList.remove(
      "flex"
    );
  }


  // Abrir modal de alteração de senha

  btnAlterarSenha.addEventListener(
    "click",
    () => {

      if (!usuario || salvando) return;

      fecharEdicaoEmail();

      fecharEdicaoTelefone();

      erroSenhaUsuario.textContent = "";

      erroSenhaUsuario.classList.add(
        "hidden"
      );

      modalAlterarSenha.classList.remove(
        "hidden"
      );

      modalAlterarSenha.classList.add(
        "flex"
      );

      senhaAtual.focus();
    }
  );


  btnCancelarSenha.addEventListener(
    "click",
    () => {

      if (!salvando) {
        fecharModalSenha();
      }
    }
  );


  // Mostrar ou ocultar senha

  function alternarVisibilidadeSenha(
    input,
    botao
  ) {

    const mostrar =
      input.type === "password";

    input.type = mostrar
      ? "text"
      : "password";

    botao.setAttribute(
      "aria-label",
      mostrar ? "Ocultar senha" : "Mostrar senha"
    );
  }


  btnMostrarNovaSenha.addEventListener(
    "click",
    () => {

      alternarVisibilidadeSenha(
        novaSenha,
        btnMostrarNovaSenha
      );
    }
  );


  btnMostrarConfirmacaoSenha.addEventListener(
    "click",
    () => {

      alternarVisibilidadeSenha(
        confirmarNovaSenha,
        btnMostrarConfirmacaoSenha
      );
    }
  );


  // Fechar modal com ESC

  document.addEventListener(
    "keydown",
    (evento) => {

      if (
        evento.key === "Escape" &&
        !salvando &&
        !modalAlterarSenha.classList.contains(
          "hidden"
        )
      ) {

        fecharModalSenha();
      }
    }
  );


  // Salvar nova senha

  btnSalvarSenha.addEventListener(
    "click",
    async () => {

      if (!usuario || salvando) return;

      const atual = senhaAtual.value;

      const nova = novaSenha.value;

      const confirmar =
        confirmarNovaSenha.value;


      function exibirErro(texto) {

        erroSenhaUsuario.textContent = texto;

        erroSenhaUsuario.classList.remove(
          "hidden"
        );
      }


      erroSenhaUsuario.textContent = "";

      erroSenhaUsuario.classList.add(
        "hidden"
      );


      // Validação da senha atual
      if (!atual) {

        exibirErro(
          "Informe sua senha atual."
        );

        return;
      }


      // Validação da nova senha
      if (
        nova.length < 8 ||
        nova.length > 50 ||
        new TextEncoder().encode(nova).length > 72
      ) {

        exibirErro(
          "A nova senha deve ter de 8 a 50 caracteres e no máximo 72 bytes."
        );

        return;
      }


      // Confirmação da senha
      if (confirmar !== nova) {

        exibirErro(
          "As senhas não coincidem."
        );

        return;
      }


      salvando = true;

      btnSalvarSenha.disabled = true;

      btnSalvarSenha.textContent =
        "Salvando...";


      try {

        // Envia para a API Java
        await chamarAPI(
          "/api/perfil/senha",
          {
            method: "PATCH",

            body: JSON.stringify({
              senhaAtual: atual,
              novaSenha: nova
            })
          }
        );


        // O backend encerra todas as sessões
        // após a troca de senha

        sessionStorage.removeItem("token");

        sessionStorage.removeItem("usuario");

        fecharModalSenha();

        usuario = null;

        habilitarEdicao(false);

        mostrarStatus(
          "Senha alterada com sucesso! " +
          "Sua sessão foi encerrada. " +
          "Faça login novamente.",
          "sucesso"
        );


      } catch (erro) {

        if (erro.status === 401) {

          fecharModalSenha();

          mensagemErro(erro);

        } else {

          exibirErro(
            mensagemErro(erro)
          );
        }

      } finally {

        salvando = false;

        btnSalvarSenha.disabled = false;

        btnSalvarSenha.textContent =
          "Salvar";
      }
    }
  );


  // Foto do usuário

  // Nesta etapa a foto é salva somente
  // no navegador, não no MongoDB

  function chaveFotoLocal() {

    return usuario
      ? `psicomManagerFoto:${usuario.id}`
      : "";
  }


  // Atualizar foto

  function atualizarFoto() {

    if (!usuario) return;

    let foto = null;

    try {

      foto = localStorage.getItem(
        chaveFotoLocal()
      );

    } catch (erro) {

      console.warn(
        "O armazenamento local de fotos está indisponível."
      );
    }


    if (
      foto &&
      /^data:image\/(jpeg|png|webp);base64,/.test(foto)
    ) {

      fotoUsuario.src = foto;

      fotoUsuario.classList.remove(
        "hidden"
      );

      fotoPlaceholder.classList.add(
        "hidden"
      );

    } else {

      fotoUsuario.removeAttribute("src");

      fotoUsuario.classList.add(
        "hidden"
      );

      fotoPlaceholder.textContent =
        (usuario.nome || "?")
          .charAt(0)
          .toUpperCase();

      fotoPlaceholder.classList.remove(
        "hidden"
      );
    }
  }


  function mostrarErroFoto(texto) {

    erroFotoUsuario.textContent = texto;

    erroFotoUsuario.classList.remove(
      "hidden"
    );
  }


  btnAlterarFoto.addEventListener(
    "click",
    () => {

      if (usuario && !salvando) {

        inputFotoUsuario.click();
      }
    }
  );


  // Validar e salvar foto localmente

  inputFotoUsuario.addEventListener(
    "change",
    () => {

      if (!usuario) return;

      erroFotoUsuario.textContent = "";

      erroFotoUsuario.classList.add(
        "hidden"
      );

      const arquivo =
        inputFotoUsuario.files[0];

      if (!arquivo) return;


      // Formatos permitidos
      const tiposPermitidos = [
        "image/jpeg",
        "image/png",
        "image/webp"
      ];


      if (!tiposPermitidos.includes(arquivo.type)) {

        mostrarErroFoto(
          "Escolha uma imagem JPG, PNG ou WEBP."
        );

        return;
      }


      // Limite de 2 MB
      if (arquivo.size > 2 * 1024 * 1024) {

        mostrarErroFoto(
          "A imagem deve ter no máximo 2 MB."
        );

        return;
      }


      const usuarioId = usuario.id;

      const leitor = new FileReader();


      leitor.addEventListener(
        "load",
        () => {

          // Confere se o usuário continua sendo
          // o mesmo que selecionou a imagem

          if (
            !usuario ||
            usuario.id !== usuarioId
          ) {

            return;
          }


          try {

            localStorage.setItem(
              chaveFotoLocal(),
              leitor.result
            );

            atualizarFoto();

            mostrarStatus(
              "Foto salva apenas neste navegador. " +
              "Ela ainda não é sincronizada com o MongoDB.",
              "normal"
            );

          } catch (erro) {

            mostrarErroFoto(
              "Não foi possível salvar a foto neste navegador."
            );
          }
        }
      );


      leitor.readAsDataURL(arquivo);

      inputFotoUsuario.value = "";
    }
  );


  // Inicialização

  // Carrega os dados reais do usuário
  // que realizou o login

  carregarPerfil();

});
