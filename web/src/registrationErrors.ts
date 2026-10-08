export interface RegistrationErrorCopy {
  defaultError: string;
  verificationError: string;
  invalidRequest: string;
}

/** Maps the registration endpoint status to translated copy, never the raw server message. */
export function registrationErrorMessage(status: number, copy: RegistrationErrorCopy): string {
  if (status === 401) return copy.verificationError;
  if (status === 400) return copy.invalidRequest;
  return copy.defaultError;
}
