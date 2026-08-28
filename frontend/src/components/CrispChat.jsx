import { useEffect } from "react";
import { Crisp } from "crisp-sdk-web";

// Integração Crisp (suporte ao cliente) — apenas na landing page.
//
// Abas do chatbox (conforme escopo):
//   1. Chat (Suporte)        -> funcionando (precisa apenas do VITE_CRISP_WEBSITE_ID)
//   2. Central de Ajuda      -> ativar "Helpdesk" no painel do Crisp
//                              (Settings > Setup & Integrations > Chatbox > Helpdesk)
//   3. Pesquisa              -> já vem junto da Central de Ajuda (busca de artigos)
//
// O estilo preto/branco (padrão Gendaz) é aplicado via setColorTheme.
// Ajustes finos de cor (main/text/accent) devem ser feitos no painel do Crisp,
// pois o SDK web não expõe setter para isso.

export function CrispChat() {
  useEffect(() => {
    const crispWebsiteId = import.meta.env.VITE_CRISP_WEBSITE_ID;
    if (!crispWebsiteId) {
      console.warn(
        "Crisp: VITE_CRISP_WEBSITE_ID não configurado. Defina a variável de ambiente no Render."
      );
      return;
    }

    // Conecta ao workspace Crisp (documentação oficial: crisp-sdk-web)
    Crisp.configure(crispWebsiteId, {
      locale: "pt-br",
    });

    // Tema Gendaz (preto/branco). #111827 = grafite escuro do padrão Gendaz.
    Crisp.setColorTheme("#111827");

    // Remove o Crisp ao desmontar a landing page
    return () => {
      Crisp.reset();
    };
  }, []);

  return null;
}
