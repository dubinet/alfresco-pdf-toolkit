package org.alfresco.extension.pdftoolkit.repo.action.executer;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.PRStream;
import com.itextpdf.text.pdf.PdfName;
import com.itextpdf.text.pdf.PdfNumber;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.parser.PdfImageObject;
import com.itextpdf.text.exceptions.InvalidImageException;
import com.itextpdf.text.exceptions.UnsupportedPdfException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.IIOException;
/**
 * Created by peter on 2014-01-10.
 */
public class PDFCompressActionExecuter extends BasePDFActionExecuter {

    /**
     * The logger
     */
    private static Log logger                   = LogFactory.getLog(PDFCompressActionExecuter.class);

    /**
     * Action constants
     */
    public static final String NAME                     = "pdf-compress";
    public static final String PARAM_DESTINATION_FOLDER = "destination-folder";
    public static final String PARAM_COMPRESSION_LEVEL    = "compression-level";
    public static final String PARAM_IMAGE_COMPRESSION_LEVEL    = "image-compression-level";

    /**
     * Add parameter definitions
     */
    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_FOLDER, DataTypeDefinition.NODE_REF, true, getParamDisplayLabel(PARAM_DESTINATION_FOLDER)));
        paramList.add(new ParameterDefinitionImpl(PARAM_COMPRESSION_LEVEL, DataTypeDefinition.INT, false, getParamDisplayLabel(PARAM_COMPRESSION_LEVEL)));
        paramList.add(new ParameterDefinitionImpl(PARAM_IMAGE_COMPRESSION_LEVEL, DataTypeDefinition.INT, false, getParamDisplayLabel(PARAM_IMAGE_COMPRESSION_LEVEL)));
    }

    /**
     * @see org.alfresco.repo.action.executer.ActionExecuterAbstractBase#executeImpl(org.alfresco.service.cmr.repository.NodeRef,
     * org.alfresco.service.cmr.repository.NodeRef)
     */
    @Override
    protected void executeImpl(Action ruleAction, NodeRef actionedUponNodeRef)
    {
        if (serviceRegistry.getNodeService().exists(actionedUponNodeRef) == false)
        {
            // node doesn't exist - can't do anything
            return;
        }

        ContentReader actionedUponContentReader = getReader(actionedUponNodeRef);

        if (actionedUponContentReader != null)
        {
            // Compress the document with the requested options
            doCompress(ruleAction, actionedUponNodeRef, actionedUponContentReader);
        }
        else
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Can't execute rule: \n" + "   node: " + actionedUponNodeRef + "\n" + "   reader: "
                        + actionedUponContentReader + "\n" + "   action: " + this);
            }
        }

        //set a noderef as the result
        ruleAction.setParameterValue(PARAM_RESULT, actionedUponNodeRef);
    }

    protected void doCompress(Action ruleAction, NodeRef actionedUponNodeRef, ContentReader actionedUponContentReader)
    {
        Map<String, Object> options = new HashMap<String, Object>(INITIAL_OPTIONS);
        options.put(PARAM_DESTINATION_FOLDER, ruleAction.getParameterValue(PARAM_DESTINATION_FOLDER));
        options.put(PARAM_COMPRESSION_LEVEL, ruleAction.getParameterValue(PARAM_COMPRESSION_LEVEL));
        options.put(PARAM_IMAGE_COMPRESSION_LEVEL, ruleAction.getParameterValue(PARAM_IMAGE_COMPRESSION_LEVEL));
        options.put(PARAM_INPLACE, ruleAction.getParameterValue(PARAM_INPLACE));

        try
        {
            this.action(ruleAction, actionedUponNodeRef, actionedUponContentReader, options);
        }
        catch (AlfrescoRuntimeException e)
        {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * @param reader
     * @param writer
     * @param options
     * @throws Exception
     */
    protected final void action(Action ruleAction, NodeRef actionedUponNodeRef, ContentReader actionedUponContentReader,
                                Map<String, Object> options)
    {
        PdfStamper stamper = null;
        File tempDir = null;
        ContentWriter writer = null;

        float Factor = 0.5f;

        switch ((Integer)options.get(PARAM_IMAGE_COMPRESSION_LEVEL))
        {
            case 9:
                Factor = 0.1f;
                break;
            case 8:
                Factor = 0.2f;
                break;
            case 7:
                Factor = 0.3f;
                break;
            case 6:
                Factor = 0.4f;
                break;
            case 5:
                Factor = 0.5f;
                break;
            case 4:
                Factor = 0.6f;
                break;
            case 3:
                Factor = 0.7f;
                break;
            case 2:
                Factor = 0.8f;
                break;
            case 1:
                Factor = 0.9f;
                break;
        }

        try
        {

            // get temp file
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + actionedUponNodeRef.getId());
            tempDir.mkdir();
            File file = new File(tempDir, serviceRegistry.getFileFolderService().getFileInfo(actionedUponNodeRef).getName());

            int compression_level= (Integer)options.get(PARAM_COMPRESSION_LEVEL);

            Boolean inplace = Boolean.valueOf(String.valueOf(options.get(PARAM_INPLACE)));

            PdfReader reader = new PdfReader(actionedUponContentReader.getContentInputStream());
            Document.compress = true;

            int n = reader.getXrefSize();
            PdfObject object;
            PRStream stream;
            // Look for image and manipulate image stream
            for (int i = 0; i < n; i++) {
                object = reader.getPdfObject(i);
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



            stamper = new PdfStamper(reader, new FileOutputStream(file), PdfWriter.VERSION_1_7);

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
                logger.debug("Executing: \n" + "   node: " + actionedUponNodeRef + "\n" + "   reader: "
                        + actionedUponContentReader + "\n" + "   action: " + this + "\n" + "   compression: " + compression_level);
            }

            int total = reader.getNumberOfPages() +  1;
            for (int i = 1; i < total; i++) {
                reader.setPageContent(i, reader.getPageContent(i));
            }

            stamper.close();


            // write out to destination
            NodeRef destinationNode = createDestinationNode(file.getName(),
                    (NodeRef)ruleAction.getParameterValue(PARAM_DESTINATION_FOLDER), actionedUponNodeRef, inplace);

            writer = serviceRegistry.getContentService().getWriter(destinationNode, ContentModel.PROP_CONTENT, true);

            writer.setEncoding(actionedUponContentReader.getEncoding());
            writer.setMimetype(FILE_MIMETYPE);
            writer.putContent(file);
            file.delete();

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
            if (tempDir != null)
            {
                try
                {
                    tempDir.delete();
                }
                catch (Exception ex)
                {
                    throw new AlfrescoRuntimeException(ex.getMessage(), ex);
                }
            }

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
