-- 1. Création du bucket (si non existant)
INSERT INTO storage.buckets (id, name, public)
VALUES ('admin-assets', 'admin-assets', true)
ON CONFLICT (id) DO NOTHING;

-- 2. Supprimer les anciennes politiques si elles existent (pour éviter les doublons)
DROP POLICY IF EXISTS "Seul l'admin peut modifier l'avatar" ON storage.objects;
DROP POLICY IF EXISTS "Les étudiants peuvent voir l'avatar" ON storage.objects;

-- 3. Policy : INSERT/UPDATE/DELETE pour l'admin
CREATE POLICY "Seul l'admin peut modifier l'avatar"
ON storage.objects
FOR ALL -- Couvre INSERT, UPDATE, DELETE, SELECT
TO authenticated
USING (
  bucket_id = 'admin-assets' AND 
  (auth.jwt() ->> 'email') = 'admin@emsi.ma'
)
WITH CHECK (
  bucket_id = 'admin-assets' AND 
  (auth.jwt() ->> 'email') = 'admin@emsi.ma'
);

-- 4. Policy : SELECT pour tous les utilisateurs authentifiés
CREATE POLICY "Les étudiants peuvent voir l'avatar"
ON storage.objects
FOR SELECT
TO authenticated
USING (bucket_id = 'admin-assets');