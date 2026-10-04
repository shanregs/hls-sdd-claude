export interface MatrixRow {
  id: string;
  role: string;
  module: string;
  action: string;
  granted: boolean;
}

export interface MatrixEntry {
  role: string;
  module: string;
  action: string;
  granted: boolean;
}

/** One module and, per role, the actions that can be granted; an empty list means "not applicable". */
export interface MatrixModule {
  module: string;
  eligible: Record<string, string[]>;
}

export interface MatrixResponse {
  entries: MatrixEntry[];
  modules: MatrixModule[];
}
