import { render, screen } from "@testing-library/react";
import App from "./App";

test("renders PostFusion brand on landing", () => {
  render(<App />);
  expect(screen.getAllByText(/PostFusion/i).length).toBeGreaterThan(0);
  expect(screen.getByText(/One Click, Every Platform/i)).toBeInTheDocument();
});
