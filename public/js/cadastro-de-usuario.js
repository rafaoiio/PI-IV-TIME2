
document.addEventListener("DOMContentLoaded", function () {

  // Elementos da tela

  const formCadastro =
    document.getElementById("formCadastro");

  const etapaDados =
    document.getElementById("etapaDados");

  const etapaSenha =
    document.getElementById("etapaSenha");

  const nomeInput =
    document.getElementById("nome");

  const emailInput =
    document.getElementById("email");

  const celularInput =
    document.getElementById("celular");

  const senhaInput =
    document.getElementById("senha");

  const confirmarSenhaInput =
    document.getElementById("confirmarSenha");

  const erroNome =
    document.getElementById("erroNome");

  const erroEmail =
    document.getElementById("erroEmail");

  const erroCelular =
    document.getElementById("erroCelular");

  const erroSenha =
    document.getElementById("erroSenha");

  const erroConfirmarSenha =
    document.getElementById("erroConfirmarSenha");

  const btnProximo =
    document.getElementById("btnProximo");

  const btnVoltar =
    document.getElementById("btnVoltar");

  const btnMostrarSenha =
    document.getElementById("btnMostrarSenha");

  const btnMostrarConfirmacao =
    document.getElementById("btnMostrarConfirmacao");

  const btnCadastrar =
    document.getElementById("btnCadastrar");

  const mensagemCadastro =
    document.getElementById("mensagemCadastro");

  const modalSucesso =
    document.getElementById("modalSucesso");

  const caixaModalSucesso =
    document.getElementById("caixaModalSucesso");

  const API_URL =
    "http://localhost:8080/api/usuarios";

  let etapaAtual = 1;
  let enviandoCadastro = false;


  // Mensagens de erro

  function mostrarErro(input, elementoErro, mensagem) {

    elementoErro.textContent = mensagem;
    elementoErro.classList.remove("hidden");

    input.setAttribute(
      "aria-invalid",
      "true"
    );

  }


  function removerErro(input, elementoErro) {

    elementoErro.textContent = "";
    elementoErro.classList.add("hidden");

    input.removeAttribute("aria-invalid");

  }


  function mostrarErroCadastro(mensagem) {

    mensagemCadastro.textContent = mensagem;
    mensagemCadastro.classList.remove("hidden");

  }


  function limparErroCadastro() {

    mensagemCadastro.textContent = "";
    mensagemCadastro.classList.add("hidden");

  }


  function validarNome() {

    const nome = nomeInput.value.trim();

    const partes = nome
      .split(/\s+/)
      .filter(Boolean);

    if (nome === "") {

      mostrarErro(
        nomeInput,
        erroNome,
        "Informe seu nome completo."
      );

      return false;
    }

    if (
      nome.length < nomeInput.minLength ||
      nome.length > nomeInput.maxLength
    ) {

      mostrarErro(
        nomeInput,
        erroNome,
        "Digite um nome válido."
      );

      return false;
    }

    if (partes.length < 2) {

      mostrarErro(
        nomeInput,
        erroNome,
        "Digite seu nome e sobrenome."
      );

      return false;
    }

    removerErro(nomeInput, erroNome);
    return true;

  }


  function validarEmail() {

    const email = emailInput.value.trim();

    if (email === "") {

      mostrarErro(
        emailInput,
        erroEmail,
        "Informe seu e-mail."
      );

      return false;
    }

    if (
      emailInput.validity.typeMismatch ||
      email.length > emailInput.maxLength
    ) {

      mostrarErro(
        emailInput,
        erroEmail,
        "Digite um e-mail válido."
      );

      return false;
    }

    removerErro(emailInput, erroEmail);
    return true;

  }


  function obterNumerosCelular(valor) {

    const texto = String(valor).trim();

    let numeros =
      texto.replace(/\D/g, "");

    if (
      texto.startsWith("+55") ||
      (
        numeros.length > 11 &&
        numeros.startsWith("55")
      )
    ) {

      numeros = numeros.substring(2);

    }

    return numeros.substring(0, 11);

  }


  function aplicarMascaraCelular(valor) {

    const numeros =
      obterNumerosCelular(valor);

    if (!numeros) return "";

    let resultado =
      "+55 (" + numeros.substring(0, 2);

    if (numeros.length >= 2) {
      resultado += ")";
    }

    if (numeros.length > 2) {
      resultado += " " + numeros.substring(2, 7);
    }

    if (numeros.length > 7) {
      resultado += "-" + numeros.substring(7, 11);
    }

    return resultado;

  }


  function validarCelular() {

    const numeros =
      obterNumerosCelular(celularInput.value);

    if (numeros.length === 0) {

      mostrarErro(
        celularInput,
        erroCelular,
        "Informe seu celular."
      );

      return false;
    }

    if (numeros.length !== 11) {

      mostrarErro(
        celularInput,
        erroCelular,
        "Digite um celular válido com DDD."
      );

      return false;
    }

    removerErro(celularInput, erroCelular);
    return true;

  }


  function validarSenha() {

    const senha = senhaInput.value;

    if (senha === "") {

      mostrarErro(
        senhaInput,
        erroSenha,
        "Informe sua senha."
      );

      return false;
    }

    if (
      senha.length < 8 ||
      senha.length > 50
    ) {

      mostrarErro(
        senhaInput,
        erroSenha,
        "A senha deve ter entre 8 e 50 caracteres."
      );

      return false;
    }

    if (
      new TextEncoder().encode(senha).length > 72
    ) {

      mostrarErro(
        senhaInput,
        erroSenha,
        "A senha não pode ultrapassar 72 bytes."
      );

      return false;
    }

    removerErro(senhaInput, erroSenha);
    return true;

  }


  function validarConfirmacaoSenha() {

    const confirmacao =
      confirmarSenhaInput.value;

    if (confirmacao === "") {

      mostrarErro(
        confirmarSenhaInput,
        erroConfirmarSenha,
        "Confirme sua senha."
      );

      return false;
    }

    if (confirmacao !== senhaInput.value) {

      mostrarErro(
        confirmarSenhaInput,
        erroConfirmarSenha,
        "As senhas não coincidem."
      );

      return false;
    }

    removerErro(
      confirmarSenhaInput,
      erroConfirmarSenha
    );

    return true;

  }


  function validarEtapaDados() {

    const nomeValido = validarNome();
    const emailValido = validarEmail();
    const celularValido = validarCelular();

    return (
      nomeValido &&
      emailValido &&
      celularValido
    );

  }


  function irParaEtapaSenha() {

    if (enviandoCadastro) return;

    limparErroCadastro();

    if (!validarEtapaDados()) {

      if (!nomeInput.hasAttribute("aria-invalid")) {

        if (emailInput.hasAttribute("aria-invalid")) {
          emailInput.focus();
        } else {
          celularInput.focus();
        }

      } else {
        nomeInput.focus();
      }

      return;
    }

    etapaDados.classList.add("hidden");
    etapaSenha.classList.remove("hidden");

    etapaAtual = 2;
    senhaInput.focus();

  }


  function voltarParaEtapaDados() {

    if (enviandoCadastro) return;

    etapaSenha.classList.add("hidden");
    etapaDados.classList.remove("hidden");

    etapaAtual = 1;

    limparErroCadastro();
    nomeInput.focus();

  }


  function alternarVisibilidadeSenha(input, botao) {

    const estaOculta =
      input.type === "password";

    input.type =
      estaOculta ? "text" : "password";

    botao.setAttribute(
      "aria-label",
      estaOculta ? "Ocultar senha" : "Mostrar senha"
    );

    botao.setAttribute(
      "aria-pressed",
      String(estaOculta)
    );

  }


  function exibirModalSucesso() {

    // Mostra o modal que já existe no HTML

    modalSucesso.classList.remove("hidden");
    modalSucesso.classList.add("flex");

    document.body.classList.add("overflow-hidden");

    document.querySelector("main").inert = true;
    modalSucesso.focus();


    // Ativa a transição definida com Tailwind

    requestAnimationFrame(() => {

      requestAnimationFrame(() => {

        caixaModalSucesso.classList.remove(
          "opacity-0",
          "translate-y-3",
          "scale-95"
        );

        caixaModalSucesso.classList.add(
          "opacity-100",
          "translate-y-0",
          "scale-100"
        );

      });

    });


    // Aguarda 1,5 segundo e abre o login

    setTimeout(() => {

      window.location.href =
        "./login-usuario.html";

    }, 1500);

  }


  // Eventos dos campos

  nomeInput.addEventListener(
    "blur",
    validarNome
  );

  emailInput.addEventListener(
    "blur",
    validarEmail
  );

  celularInput.addEventListener(
    "blur",
    validarCelular
  );

  senhaInput.addEventListener(
    "blur",
    validarSenha
  );

  confirmarSenhaInput.addEventListener(
    "blur",
    validarConfirmacaoSenha
  );


  // Validação durante a digitação

  nomeInput.addEventListener("input", () => {

    limparErroCadastro();

    if (!erroNome.classList.contains("hidden")) {
      validarNome();
    }

  });


  emailInput.addEventListener("input", () => {

    limparErroCadastro();

    if (!erroEmail.classList.contains("hidden")) {
      validarEmail();
    }

  });


  celularInput.addEventListener("input", () => {

    limparErroCadastro();

    celularInput.value =
      aplicarMascaraCelular(celularInput.value);

    if (!erroCelular.classList.contains("hidden")) {
      validarCelular();
    }

  });


  senhaInput.addEventListener("input", () => {

    limparErroCadastro();

    if (!erroSenha.classList.contains("hidden")) {
      validarSenha();
    }

    if (
      !erroConfirmarSenha.classList.contains("hidden")
    ) {
      validarConfirmacaoSenha();
    }

  });


  confirmarSenhaInput.addEventListener("input", () => {

    limparErroCadastro();

    if (
      !erroConfirmarSenha.classList.contains("hidden")
    ) {
      validarConfirmacaoSenha();
    }

  });


  // Eventos dos botões

  btnProximo.addEventListener(
    "click",
    irParaEtapaSenha
  );

  btnVoltar.addEventListener(
    "click",
    voltarParaEtapaDados
  );

  btnMostrarSenha.addEventListener("click", () => {

    alternarVisibilidadeSenha(
      senhaInput,
      btnMostrarSenha
    );

  });

  btnMostrarConfirmacao.addEventListener("click", () => {

    alternarVisibilidadeSenha(
      confirmarSenhaInput,
      btnMostrarConfirmacao
    );

  });


  // Envio para a API Java

  formCadastro.addEventListener(
    "submit",
    async function (event) {

      event.preventDefault();

      if (enviandoCadastro) return;


      // Enter na primeira etapa

      if (etapaAtual === 1) {

        irParaEtapaSenha();
        return;

      }

      limparErroCadastro();


      // Confere novamente os dados pessoais

      if (!validarEtapaDados()) {

        etapaSenha.classList.add("hidden");
        etapaDados.classList.remove("hidden");

        etapaAtual = 1;

        if (nomeInput.hasAttribute("aria-invalid")) {
          nomeInput.focus();
        } else if (emailInput.hasAttribute("aria-invalid")) {
          emailInput.focus();
        } else {
          celularInput.focus();
        }

        return;

      }


      // Valida senha e confirmação

      const senhaValida = validarSenha();

      const confirmacaoValida =
        validarConfirmacaoSenha();

      if (!senhaValida || !confirmacaoValida) {

        if (!senhaValida) {
          senhaInput.focus();
        } else {
          confirmarSenhaInput.focus();
        }

        return;

      }


      // Prepara os dados para o backend

      // Não envia o campo confirmarSenha

      const dadosCadastro = {

        nome: nomeInput.value.trim(),

        email: emailInput.value
          .trim()
          .toLowerCase(),

        celular: obterNumerosCelular(
          celularInput.value
        ),

        senha: senhaInput.value

      };


      // Impede envios duplicados

      enviandoCadastro = true;

      btnCadastrar.disabled = true;
      btnVoltar.disabled = true;

      btnCadastrar.textContent = "Cadastrando...";

      let cadastroConfirmado = false;


      try {

        // Envia os dados ao Java

        const resposta = await fetch(API_URL, {

          method: "POST",

          headers: {
            "Content-Type": "application/json"
          },

          body: JSON.stringify(dadosCadastro)

        });


        const resultado =
          await resposta.json().catch(
            () => ({})
          );


        // Tratamento de erros da API

        if (!resposta.ok) {

          // E-mail já cadastrado

          if (resposta.status === 409) {

            etapaSenha.classList.add("hidden");
            etapaDados.classList.remove("hidden");

            etapaAtual = 1;

            mostrarErro(
              emailInput,
              erroEmail,
              resultado.mensagem ||
                "Este e-mail já está cadastrado."
            );

            emailInput.focus();

          } else {

            mostrarErroCadastro(
              resultado.mensagem ||
                "Não foi possível realizar o cadastro."
            );

          }

          return;

        }


        // Cadastro realizado com sucesso

        cadastroConfirmado = true;

        // O Java confirmou o cadastro no banco

        // Agora mostramos o modal do HTML

        exibirModalSucesso();


      } catch (erro) {

        mostrarErroCadastro(
          "Não foi possível confirmar o cadastro. " +
          "Verifique se o backend Java está ligado " +
          "e confira o e-mail antes de tentar novamente."
        );

      } finally {

        // Se houve sucesso, os botões permanecem
        // bloqueados até o redirecionamento.

        if (!cadastroConfirmado) {

          enviandoCadastro = false;

          btnCadastrar.disabled = false;
          btnVoltar.disabled = false;

          btnCadastrar.textContent = "Cadastrar";

        }

      }

    }
  );

});