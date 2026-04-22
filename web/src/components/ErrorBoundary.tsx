import React from "react";

type Props = { children: React.ReactNode };
type State = { error: Error | null };

export default class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error("[ErrorBoundary]", error, info.componentStack);
  }

  reset = () => {
    this.setState({ error: null });
  };

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 32, maxWidth: 600, margin: "40px auto", fontFamily: "system-ui" }}>
          <h1 style={{ fontSize: 28, marginBottom: 12 }}>Something went wrong.</h1>
          <p style={{ color: "#555", marginBottom: 16 }}>
            The app hit an unexpected error. Try reloading the page.
          </p>
          <button
            onClick={() => {
              this.reset();
              window.location.reload();
            }}
            style={{
              padding: "10px 20px",
              background: "#FE6A00",
              color: "white",
              border: 0,
              borderRadius: 8,
              cursor: "pointer",
            }}
          >
            Reload
          </button>
          {import.meta.env.DEV && (
            <pre style={{ marginTop: 20, padding: 12, background: "#f4f4f4", borderRadius: 8, overflow: "auto" }}>
              {this.state.error.message}
              {"\n"}
              {this.state.error.stack}
            </pre>
          )}
        </div>
      );
    }
    return this.props.children;
  }
}
