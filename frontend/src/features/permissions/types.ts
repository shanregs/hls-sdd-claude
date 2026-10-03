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
