import {
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { ApiError } from '../../api/ApiError'
import * as authApi from './authApi'
import {
  AuthContext,
  type AuthContextValue,
  type AuthStatus,
} from './authContext'
import type { AuthSession } from './types'

interface AuthProviderProps {
  children: ReactNode
  initialSession?: AuthSession | null
}

export function AuthProvider({
  children,
  initialSession,
}: AuthProviderProps) {
  const [session, setSession] = useState<AuthSession | null>(
    initialSession ?? null,
  )
  const [status, setStatus] = useState<AuthStatus>(
    initialSession === undefined
      ? 'loading'
      : initialSession
        ? 'authenticated'
        : 'unauthenticated',
  )

  useEffect(() => {
    if (initialSession !== undefined) {
      return
    }

    let active = true
    authApi
      .refreshSession()
      .then((restoredSession) => {
        if (active) {
          setSession(restoredSession)
          setStatus('authenticated')
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setSession(null)
          setStatus('unauthenticated')
          if (!(error instanceof ApiError) || error.status >= 500) {
            console.error('Unable to restore the authentication session.', error)
          }
        }
      })

    return () => {
      active = false
    }
  }, [initialSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user: session?.user ?? null,
      accessToken: session?.accessToken ?? null,
      login: async (input) => {
        const authenticatedSession = await authApi.login(input)
        setSession(authenticatedSession)
        setStatus('authenticated')
      },
      register: async (input) => {
        const authenticatedSession = await authApi.register(input)
        setSession(authenticatedSession)
        setStatus('authenticated')
      },
      logout: async () => {
        try {
          await authApi.logout()
        } finally {
          setSession(null)
          setStatus('unauthenticated')
        }
      },
    }),
    [session, status],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
