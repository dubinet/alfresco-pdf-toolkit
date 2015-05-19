package org.alfresco.extension.pdftoolkit.transformer;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.exceptions.InvalidImageException;
import com.itextpdf.text.exceptions.UnsupportedPdfException;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.transform.AbstractContentTransformer2;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.TransformationOptions;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by peter on 2014-01-13.
 */
public class ITextTransformerPDFToPDF extends AbstractContentTransformer2 {
    private static final Log logger = LogFactory.getLog(ITextTransformerPDFToPDF.class);

    @Override
    public boolean isTransformable(String sourceMimetype, String targetMimetype, TransformationOptions options) {
        if ((!MimetypeMap.MIMETYPE_PDF.equals(sourceMimetype) && !MimetypeMap.MIMETYPE_PDF.equals(targetMimetype)))
        {
            // only support (application/pdf) to (application/pdf)
            return false;
        }
        else
        {
            return true;
        }
    }

    @Override
    protected void transformInternal(ContentReader reader, ContentWriter writer, TransformationOptions options) throws Exception {
        PdfStamper stamper = null;
        File tempDir = null;

        float Factor = 0.5f;

        try
        {

            int compression_level= 9;

            PdfReader pdfreader = new PdfReader(reader.getContentInputStream());
            Document.compress = true;

            int n = pdfreader.getXrefSize();
            PdfObject object;
            PRStream stream;
            // Look for image and manipulate image stream
            for (int i = 0; i < n; i++) {
                object = pdfreader.getPdfObject(i);
                if (object == null || !object.isStream())
                    continue;
                stream = (PRStream)object;

                PdfObject pdfsubtype = stream.get(PdfName.SUBTYPE);

                if (pdfsubtype != null && pdfsubtype.toString().equals(PdfName.IMAGE.toString())) {
                    try
                    {
                        PdfImageObject image = new PdfImageObject(stream);
                        BufferedImage bi = image.getBufferedImage();
                        if (bi == null) continue;
                        int width = (int)(bi.getWidth() * Factor);
                        int height = (int)(bi.getHeight() * Factor);

                        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        AffineTransform at = AffineTransform.getScaleInstance(Factor, Factor);
                        Graphics2D g = img.createGraphics();
                        g.drawRenderedImage(bi, at);
                        ByteArrayOutputStream imgBytes = new ByteArrayOutputStream();
                        ImageIO.write(img, "JPG", imgBytes);

                        stream.clear();
                        stream.setData(imgBytes.toByteArray(), false, compression_level);
                        stream.put(PdfName.TYPE, PdfName.XOBJECT);
                        stream.put(PdfName.SUBTYPE, PdfName.IMAGE);
                        stream.put(PdfName.FILTER, PdfName.DCTDECODE);
                        stream.put(PdfName.WIDTH, new PdfNumber(width));
                        stream.put(PdfName.HEIGHT, new PdfNumber(height));
                        stream.put(PdfName.BITSPERCOMPONENT, new PdfNumber(8));
                        stream.put(PdfName.COLORSPACE, PdfName.DEVICERGB);
                    }
                    catch(InvalidImageException e)
                    {
                        continue;
                    }
                    catch(UnsupportedPdfException e)
                    {
                        continue;
                    }
                    catch(IIOException e)
                    {
                        continue;
                    }

                }
            }



            stamper = new PdfStamper(pdfreader, writer.getContentOutputStream(), PdfWriter.VERSION_1_7);

            if(compression_level < 9)
            {
                stamper.getWriter().setCompressionLevel(compression_level);
            }
            else
            {
                stamper.getWriter().setCompressionLevel(9);
                stamper.setFullCompression();
            }

            if (logger.isDebugEnabled())
            {
                logger.debug("Executing ITextTransformerPDFToPDF Compression");
            }

            int total = pdfreader.getNumberOfPages() +  1;
            for (int i = 1; i < total; i++) {
                pdfreader.setPageContent(i, pdfreader.getPageContent(i));
            }

            stamper.close();


        }
        catch (IOException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        catch (DocumentException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
        finally
        {

            if (stamper != null)
            {
                try
                {
                    stamper.close();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }
        }
    }
}
