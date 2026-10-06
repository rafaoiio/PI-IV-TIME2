/* =========================================
   ELEMENTOS DO FORMULÁRIO
========================================= */

const formCadastro =
  document.getElementById("formCadastro");


/* Etapas */

const etapaDados =
  document.getElementById("etapaDados");

const etapaSenha =
  document.getElementById("etapaSenha");


/* Botões */

const btnProximo =
  document.getElementById("btnProximo");

const btnVoltar =
  document.getElementById("btnVoltar");


/* Dados pessoais */

const nomeInput =
  document.getElementById("nome");

const emailInput =
  document.getElementById("email");

const celularInput =
  document.getElementById("celular");


/* Senhas */

const senhaInput =
  document.getElementById("senha");

const confirmarSenhaInput =
  document.getElementById("confirmarSenha");


/* Mensagens de erro */

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


/* Botões dos olhos */

const btnMostrarSenha =
  document.getElementById("btnMostrarSenha");

const btnMostrarConfirmacao =
  document.getElementById("btnMostrarConfirmacao");


/* =========================================
   CONTROLE DA ETAPA ATUAL
========================================= */

let etapaAtual = 1;


/* =========================================
   FUNÇÕES DE ERRO
========================================= */

function mostrarErro(
  input,
  elementoErro,
  mensagem
) {

  elementoErro.textContent =
    mensagem;


  elementoErro.classList.remove(
    "hidden"
  );


  input.setAttribute(
    "aria-invalid",
    "true"
  );

}


function removerErro(
  input,
  elementoErro
) {

  elementoErro.textContent =
    "";


  elementoErro.classList.add(
    "hidden"
  );


  input.removeAttribute(
    "aria-invalid"
  );

}


/* =========================================
   VALIDAÇÃO DO NOME
========================================= */

function validarNome() {

  const nome =
    nomeInput.value.trim();


  const partesNome =
    nome
      .split(/\s+/)
      .filter(
        parte => parte.length > 0
      );


  if (nome === "") {

    mostrarErro(
      nomeInput,
      erroNome,
      "Informe seu nome completo."
    );


    return false;

  }


  if (
    nome.length < nomeInput.minLength
  ) {

    mostrarErro(
      nomeInput,
      erroNome,
      "Digite um nome válido."
    );


    return false;

  }


  if (partesNome.length < 2) {

    mostrarErro(
      nomeInput,
      erroNome,
      "Digite seu nome e sobrenome."
    );


    return false;

  }


  removerErro(
    nomeInput,
    erroNome
  );


  return true;

}


/* =========================================
   VALIDAÇÃO DO E-MAIL
========================================= */

function validarEmail() {

  const email =
    emailInput.value.trim();


  if (email === "") {

    mostrarErro(
      emailInput,
      erroEmail,
      "Informe seu e-mail."
    );


    return false;

  }


  if (
    emailInput.validity.typeMismatch
  ) {

    mostrarErro(
      emailInput,
      erroEmail,
      "Digite um e-mail válido."
    );


    return false;

  }


  removerErro(
    emailInput,
    erroEmail
  );


  return true;

}


/* =========================================
   MÁSCARA DO CELULAR
========================================= */

celularInput.addEventListener(
  "input",
  function () {

    celularInput.value =
      aplicarMascaraCelular(
        celularInput.value
      );


    if (
      !erroCelular.classList.contains(
        "hidden"
      )
    ) {

      validarCelular();

    }

  }
);


function obterNumerosCelular(valor) {

  let numeros =
    String(valor).replace(
      /\D/g,
      ""
    );


  /*
    Se o valor já começar com 55,
    removemos o código do Brasil para
    trabalhar apenas com DDD + celular.
  */
  if (
    numeros.length > 11 &&
    numeros.startsWith("55")
  ) {

    numeros =
      numeros.substring(2);

  }


  return numeros.substring(
    0,
    11
  );

}


function aplicarMascaraCelular(valor) {

  const numeros =
    obterNumerosCelular(valor);


  if (numeros.length === 0) {

    return "";

  }


  let resultado =
    "+55";


  if (numeros.length > 0) {

    resultado +=
      " (" +
      numeros.substring(
        0,
        Math.min(2, numeros.length)
      );

  }


  if (numeros.length >= 2) {

    resultado += ")";

  }


  if (numeros.length > 2) {

    resultado +=
      " " +
      numeros.substring(
        2,
        Math.min(7, numeros.length)
      );

  }


  if (numeros.length > 7) {

    resultado +=
      "-" +
      numeros.substring(
        7,
        11
      );

  }


  return resultado;

}


/* =========================================
   VALIDAÇÃO DO CELULAR
========================================= */

function validarCelular() {

  const numeros =
    obterNumerosCelular(
      celularInput.value
    );


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


  removerErro(
    celularInput,
    erroCelular
  );


  return true;

}


/* =========================================
   VALIDAÇÃO DA SENHA
========================================= */

function validarSenha() {

  const senha =
    senhaInput.value;


  if (senha === "") {

    mostrarErro(
      senhaInput,
      erroSenha,
      "Informe uma senha."
    );


    return false;

  }


  if (
    senha.length < senhaInput.minLength
  ) {

    mostrarErro(
      senhaInput,
      erroSenha,
      "A senha deve possuir pelo menos 8 caracteres."
    );


    return false;

  }


  removerErro(
    senhaInput,
    erroSenha
  );


  return true;

}


/* =========================================
   VALIDAÇÃO DA CONFIRMAÇÃO
========================================= */

function validarConfirmacaoSenha() {

  const senha =
    senhaInput.value;


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


  if (senha !== confirmacao) {

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


/* =========================================
   VALIDAÇÃO AO SAIR DOS CAMPOS
========================================= */

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


/* =========================================
   VALIDA NOVAMENTE ENQUANTO CORRIGE
========================================= */

nomeInput.addEventListener(
  "input",
  function () {

    if (
      !erroNome.classList.contains(
        "hidden"
      )
    ) {

      validarNome();

    }

  }
);


emailInput.addEventListener(
  "input",
  function () {

    if (
      !erroEmail.classList.contains(
        "hidden"
      )
    ) {

      validarEmail();

    }

  }
);


senhaInput.addEventListener(
  "input",
  function () {

    if (
      !erroSenha.classList.contains(
        "hidden"
      )
    ) {

      validarSenha();

    }


    /*
      Se a confirmação já estiver preenchida,
      verificamos novamente quando a senha muda.
    */
    if (
      confirmarSenhaInput.value !== "" &&
      !erroConfirmarSenha.classList.contains(
        "hidden"
      )
    ) {

      validarConfirmacaoSenha();

    }

  }
);


confirmarSenhaInput.addEventListener(
  "input",
  function () {

    if (
      !erroConfirmarSenha.classList.contains(
        "hidden"
      )
    ) {

      validarConfirmacaoSenha();

    }

  }
);


/* =========================================
   IR PARA A SEGUNDA ETAPA
========================================= */

function irParaEtapaSenha() {

  const nomeValido =
    validarNome();


  const emailValido =
    validarEmail();


  const celularValido =
    validarCelular();


  if (
    !nomeValido ||
    !emailValido ||
    !celularValido
  ) {

    return;

  }


  etapaDados.classList.add(
    "hidden"
  );


  etapaSenha.classList.remove(
    "hidden"
  );


  etapaAtual = 2;


  senhaInput.focus();

}


/* Botão Próximo */

btnProximo.addEventListener(
  "click",
  irParaEtapaSenha
);


/* =========================================
   VOLTAR PARA A PRIMEIRA ETAPA
========================================= */

btnVoltar.addEventListener(
  "click",
  function () {

    etapaSenha.classList.add(
      "hidden"
    );


    etapaDados.classList.remove(
      "hidden"
    );


    etapaAtual = 1;


    /*
      Não limpamos nome, e-mail ou celular.
      Assim o usuário pode voltar sem perder
      o que já digitou.
    */

    nomeInput.focus();

  }
);


/* =========================================
   MOSTRAR / ESCONDER SENHAS
========================================= */

function alternarVisibilidadeSenha(
  input,
  botao
) {

  const estaVisivel =
    input.type === "text";


  if (estaVisivel) {

    input.type =
      "password";


    botao.setAttribute(
      "aria-label",
      "Mostrar senha"
    );

  } else {

    input.type =
      "text";


    botao.setAttribute(
      "aria-label",
      "Ocultar senha"
    );

  }

}


btnMostrarSenha.addEventListener(
  "click",
  function () {

    alternarVisibilidadeSenha(
      senhaInput,
      btnMostrarSenha
    );

  }
);


btnMostrarConfirmacao.addEventListener(
  "click",
  function () {

    alternarVisibilidadeSenha(
      confirmarSenhaInput,
      btnMostrarConfirmacao
    );

  }
);


/* =========================================
   SUBMIT DO FORMULÁRIO
========================================= */

formCadastro.addEventListener(
  "submit",
  function (event) {

    event.preventDefault();


    /*
      Se o usuário apertar Enter enquanto
      ainda estiver na primeira etapa,
      avançamos para a etapa da senha.
    */
    if (etapaAtual === 1) {

      irParaEtapaSenha();

      return;

    }


    const senhaValida =
      validarSenha();


    const confirmacaoValida =
      validarConfirmacaoSenha();


    if (
      !senhaValida ||
      !confirmacaoValida
    ) {

      return;

    }


    const dadosCadastro = {

      nome:
        nomeInput.value.trim(),

      email:
        emailInput.value
          .trim()
          .toLowerCase(),

      celular:
        celularInput.value,

      senha:
        senhaInput.value

    };


    console.log(
      "Cadastro válido:",
      dadosCadastro
    );


    /*
      FUTURAMENTE:

      fetch("/api/usuarios", {
        method: "POST",

        headers: {
          "Content-Type": "application/json"
        },

        body: JSON.stringify(
          dadosCadastro
        )
      });

              ↓
            Java
              ↓
       validação backend
              ↓
         hash da senha
              ↓
           MongoDB


      IMPORTANTE:
      a senha nunca deve ser salva no
      localStorage.
    */

  }
);