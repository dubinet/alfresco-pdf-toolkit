package se.alfresco.extensions.naming;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;

/**
 * Created by bhagyasilva on 21/05/15.
 */
public class AlfrescoDefaultExistingFileFileNameProvider implements FileNameProvider {

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    ServiceRegistry serviceRegistry;

    @Override
    public String getFileName(String filename, NodeRef existingFileNodeRef, NodeRef containerNode) {
        // Upload component was configured to find a new unique name for clashing filenames
        int counter = 1;
        String tmpFilename = filename;
        int dotIndex;

        while (existingFileNodeRef != null) {
            dotIndex = filename.lastIndexOf(".");
            if (dotIndex == 0) {
                // File didn't have a proper 'name' instead it had just a suffix and started with a ".", create "1.txt"
                tmpFilename = counter + filename;
            } else if (dotIndex > 0) {
                // Filename contained ".", create "filename-1.txt"
                tmpFilename = filename.substring(0, dotIndex) + "-" + counter + filename.substring(dotIndex);
            } else {
                // Filename didn't contain a dot at all, create "filename-1"
                tmpFilename = filename + "-" + counter;
            }

            existingFileNodeRef = serviceRegistry.getNodeService().getChildByName(containerNode, ContentModel.ASSOC_CONTAINS, tmpFilename);
            counter++;
        }
        filename = tmpFilename;
        return filename;
    }
}
