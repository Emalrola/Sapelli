package uk.ac.ucl.excites.sapelli.transmission.compression;

import java.io.IOException;

import uk.ac.ucl.excites.sapelli.transmission.compression.CompressorFactory.CompressionMode;

/**
 * Dummy compressor (leaves data unchanged)
 * 
 * @author mstevens
 *
 */
public class DummyCompressor extends Compressor
{

	@Override
	public byte[] compress(byte[] data) throws IOException
	{
		return data;
	}

	@Override
	public byte[] decompress(byte[] compressedData) throws IOException
	{
		return compressedData;
	}

	@Override
	public CompressionMode getMode()
	{
		return CompressionMode.NONE;
	}
	
}