import { Component } from "react";
import * as Sentry from "@sentry/react";

export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error("ErrorBoundary capturou um erro:", error, info);
    Sentry.captureException(error, { extra: { componentStack: info.componentStack } });
  }

  render() {
    if (this.state.hasError) {
      return (
        <div
          style={{
            minHeight: "100vh",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: 12,
            padding: 24,
            background: "#0d0c0c",
            color: "#E5E7EB",
            fontFamily: "Poppins, sans-serif",
            textAlign: "center",
          }}
        >
          <h1 style={{ fontSize: 20, fontWeight: 700 }}>Algo deu errado</h1>
          <p style={{ maxWidth: 520, opacity: 0.8 }}>
            Ocorreu um erro ao carregar a página. Recarregue ou reporte o erro abaixo.
          </p>
          <pre
            style={{
              maxWidth: 600,
              whiteSpace: "pre-wrap",
              fontSize: 12,
              background: "rgba(255,255,255,0.06)",
              padding: 12,
              borderRadius: 8,
              textAlign: "left",
            }}
          >
            {String(this.state.error?.stack || this.state.error)}
          </pre>
        </div>
      );
    }

    return this.props.children;
  }
}
