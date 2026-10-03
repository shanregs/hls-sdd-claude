import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";
import { NotAuthorizedPage } from "./NotAuthorizedPage";

describe("NotAuthorizedPage (User Story 2, FR-010)", () => {
  it("reveals nothing about the blocked screen and offers a way back to the user's dashboard", async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={["/not-authorized"]}>
        <Routes>
          <Route path="/not-authorized" element={<NotAuthorizedPage />} />
          <Route path="/dashboard" element={<div>Dashboard content</div>} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText(/not authorized/i)).toBeInTheDocument();
    expect(screen.queryByText(/dashboard content/i)).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /back to my dashboard/i }),
    );

    expect(await screen.findByText("Dashboard content")).toBeInTheDocument();
  });
});
