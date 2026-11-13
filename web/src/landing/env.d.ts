interface ImportMetaEnv {
  readonly VITE_DEMO_CODE?: string;
  /** `mock` = pure-localStorage demo without a backend; unset = the Spring Boot API under /api. */
  readonly VITE_API?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
