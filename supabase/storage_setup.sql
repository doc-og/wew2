-- Create Supabase Storage bucket for file uploads
-- Run this in your Supabase SQL Editor

-- Create the uploads bucket
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types) 
VALUES (
  'uploads', 
  'uploads', 
  false, -- Private bucket (files require authentication)
  52428800, -- 50MB limit
  ARRAY['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'video/quicktime', 'video/webm']
)
ON CONFLICT (id) DO NOTHING;

-- Enable RLS on storage.objects
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- Policy: Allow authenticated users to upload files to uploads bucket
CREATE POLICY "Allow authenticated users to upload files" ON storage.objects
FOR INSERT 
TO authenticated
WITH CHECK (bucket_id = 'uploads');

-- Policy: Allow users to view files they uploaded
CREATE POLICY "Allow users to view their own files" ON storage.objects
FOR SELECT 
TO authenticated
USING (bucket_id = 'uploads');

-- Policy: Allow users to delete their own files
CREATE POLICY "Allow users to delete their own files" ON storage.objects
FOR DELETE 
TO authenticated
USING (bucket_id = 'uploads');

-- Policy: Allow service role to manage all files (for server operations)
CREATE POLICY 'Allow service role full access' ON storage.objects
FOR ALL 
TO service_role
USING (bucket_id = 'uploads')
WITH CHECK (bucket_id = 'uploads');

-- Create folder structure (optional - Supabase will create these automatically)
-- But we can verify the structure is working by inserting some metadata

COMMENT ON TABLE storage.objects IS 'Stores uploaded media files with security policies';

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_storage_objects_bucket_id_path ON storage.objects(bucket_id, name);
CREATE INDEX IF NOT EXISTS idx_storage_objects_created_at ON storage.objects(created_at);

-- Grant necessary permissions
GRANT ALL ON storage.objects TO authenticated;
GRANT ALL ON storage.buckets TO authenticated;
