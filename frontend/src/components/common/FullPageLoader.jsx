/**
 * The whole-screen wait shown while a route chunk or the session check loads.
 * Suspense, ProtectedRoute, AdminRoute and UserRoute each drew their own version.
 */
export default function FullPageLoader({ message = 'Loading…' }) {
  return (
    <div className="min-h-screen flex flex-col justify-center items-center bg-background" role="status">
      <div className="w-10 h-10 border-4 border-outline-variant/30 border-t-primary rounded-full animate-spin" aria-hidden="true"></div>
      <p className="mt-md text-on-surface-variant font-medium">{message}</p>
    </div>
  );
}
