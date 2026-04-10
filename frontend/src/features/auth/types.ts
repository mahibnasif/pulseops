export interface AuthUser {
  id: string
  firstName: string
  lastName: string
  email: string
  emailVerified: boolean
  accountStatus: string
}

export interface AuthSession {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: AuthUser
}

export interface LoginInput {
  email: string
  password: string
}

export interface RegisterInput extends LoginInput {
  firstName: string
  lastName: string
}
