import '@testing-library/jest-dom/vitest';

import { afterEach, vi } from 'vitest';

import { terminar } from '../api/sesion';

afterEach(() => {
  // La sesión vive en memoria del módulo: cada prueba empieza sin sesión.
  terminar();
  vi.unstubAllGlobals();
});
