import { useEffect, useState } from "react";
import { Crisp } from "crisp-sdk-web";
import { MessageCircle } from "lucide-react";

// Integração Crisp (suporte ao cliente) — apenas na landing page.
//
// Abas do chatbox (conforme escopo):
//   1. Chat (Suporte)        -> funcionando (precisa apenas do VITE_CRISP_WEBSITE_ID)
//   2. Central de Ajuda      -> ativar "Helpdesk" no painel do Crisp
//                              (Settings > Setup & Integrations > Chatbox > Helpdesk)
//   3. Pesquisa              -> já vem junto da Central de Ajuda (busca de artigos)
//
// Usamos um botão fixo próprio (padrão Gendaz, canto inferior direito) para abrir
// o Crisp, escondendo o launcher padrão. O estilo preto/branco é aplicado via
// setColorTheme + CSS da classe .gendaz-crisp-launcher.

export function CrispChat() {
  const websiteId = import.meta.env.VITE_CRISP_WEBSITE_ID;
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!websiteId) {
      console.warn(
        "Crisp: VITE_CRISP_WEBSITE_ID não configurado. Defina a variável de ambiente no Render."
      );
      return;
    }

    // Conecta ao workspace Crisp (documentação oficial: crisp-sdk-web)
    Crisp.configure(websiteId, {
      locale: "pt-br",
    });

    // Tema Gendaz (preto/branco). #111827 = grafite escuro do padrão Gendaz.
    Crisp.setColorTheme("#111827");

    // Esconde o launcher padrão do Crisp (usamos o botão Gendaz)
    Crisp.onLoaded(() => {
      window.$crisp.push(["do", "launcher:hide"]);
      setLoaded(true);
    });

    // Remove o Crisp ao desmontar a landing page
    return () => {
      Crisp.reset();
    };
  }, [websiteId]);

  if (!websiteId) return null;

  function openCrisp() {
    Crisp.open();
  }

  return (
    <button
      type="button"
      className="gendaz-crisp-launcher"
      onClick={openCrisp}
      aria-label="Falar com o suporte da Gendaz"
      data-loaded={loaded ? "true" : "false"}
    >
      <MessageCircle size={26} strokeWidth={2} />
      <span className="gendaz-crisp-launcher-pulse" aria-hidden="true" />
    </button>
  );
}
