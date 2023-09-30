import torch
import torch.nn as nn
import torchvision
from torchvision.models import resnet34, mobilenet_v3_large, googlenet, efficientnet_v2_s

#dummy class, th xrhsimopoio gia na diagrafo to teleytaio layer
class Identity(nn.Module):
    def __init__(self):
        super(Identity, self).__init__()

    def forward(self, x):
        return x

class LinearLayerBlock(nn.Module):

  def __init__(self,in_dims,out_dims, dropout_use = False, relu_use = False):
    super(LinearLayerBlock,self).__init__()

    self.dropout_use = dropout_use
    self.relu_use = relu_use
    self.linear = nn.Linear(in_features = in_dims, out_features = out_dims)
    self.batchnorm = nn.BatchNorm1d(out_dims, affine = True)
    
    if relu_use:
        self.relu = nn.ReLU()
    if dropout_use:
      self.dropout = nn.Dropout(p = 0.3)

  def forward(self,x):

    output = self.linear(x)
    output = self.batchnorm(output)
    
    if self.relu_use:
        output = self.relu(output)
    if self.dropout_use:
      output = self.dropout(output)

    return output

#https://github.com/heykeetae/Self-Attention-GAN/blob/master/sagan_models.py#L8
class AttentionBlock(nn.Module):

    def __init__(self,in_dim):
        super(AttentionBlock,self).__init__()
        self.chanel_in = in_dim
        #self.activation = activation

        self.query_conv = nn.Conv2d(in_channels = in_dim , out_channels = in_dim//8 , kernel_size= 1)
        self.key_conv = nn.Conv2d(in_channels = in_dim , out_channels = in_dim//8 , kernel_size= 1)
        self.value_conv = nn.Conv2d(in_channels = in_dim , out_channels = in_dim , kernel_size= 1)
        self.gamma = nn.Parameter(torch.Tensor([0.01]))

        self.softmax = nn.Softmax(dim = -1)
    def forward(self,x):

        m_batchsize,C,width ,height = x.size()
        proj_query  = self.query_conv(x).view(m_batchsize,-1,width*height).permute(0,2,1) # B x N x C
        proj_key =  self.key_conv(x).view(m_batchsize,-1,width*height) # B x C x N
        energy =  torch.bmm(proj_query,proj_key) # bmm = batch multiplication, output size = B x N x N
        attention = self.softmax(energy)

        proj_value = self.value_conv(x).view(m_batchsize,-1,width*height) # B x C x N

        out = torch.bmm(proj_value,attention.permute(0,2,1))
        out = out.view(m_batchsize,C,width,height)

        out = self.gamma*out + x
        return out

#pretrained_model_out_dim:
#mobilenets --> 960
#resnet34 --> 520
#efficientNet v2 --> 1280
#googlenet --> 1024

#metadata_features_dim:
#PAD-UFES-20: 27
#ISIC-2019: 11

#classifier_dims:
#PAD-UFES-20:6
#ISIC-2019:8
class MetadataClassifierLinear(nn.Module):

  def __init__(self,pretrained_model, pretrained_model_out_dim, metadata_features_dim ,classifier_dims):
    super(MetadataClassifierLinear,self).__init__()
    
    self.img_extractor_dim = 256
    self.metadata_augment_dim = 64
    self.out_conv_dim = pretrained_model_out_dim
    self.pretrained_model = pretrained_model

    self.img_extractor = nn.Linear(pretrained_model_out_dim,self.img_extractor_dim)
    self.img_extractor_norm = nn.BatchNorm1d(self.img_extractor_dim, affine = True)
    
    self.metadata_augment = nn.Linear(metadata_features_dim, self.metadata_augment_dim)

    self.linearlayer_concat = LinearLayerBlock(self.img_extractor_dim + self.metadata_augment_dim, 64)#, dropout_use = True)

    self.classifier = nn.Linear(64,classifier_dims)

  def forward(self,metadata,imgs):

    img_features = self.pretrained_model(imgs).reshape(-1,self.out_conv_dim)
    img_features = self.img_extractor_norm(self.img_extractor(img_features))
    
    metadata_features = self.metadata_augment(metadata)
    
    conc_features = torch.cat((img_features,metadata_features), dim = 1)
    conc_features = self.linearlayer_concat(conc_features)

    output_features = self.classifier(conc_features)

    return output_features

#Ορίζω μία διαφορετική αρχιτεκτονική για τα metaModels.Στο μέλλον θα δοκιμάσω training με αυτό το μοντέλο.
class MetadataClassifierLinearMultiplication(nn.Module):

  def __init__(self,pretrained_model, pretrained_model_out_dim, metadata_features_dim ,classifier_dims, multiplication = False):
    super(MetadataClassifierLinearMultiplication,self).__init__()
    
    self.out_conv_dim = pretrained_model_out_dim
    self.pretrained_model = pretrained_model
    self.img_extractor_dim = 256
    self.metadata_augment_dim = 64

    self.img_extractor = nn.Linear(pretrained_model_out_dim,self.img_extractor_dim)
    self.img_extractor_norm = nn.BatchNorm1d(self.img_extractor_dim, affine = True)
    self.metadata_augment = nn.Linear(metadata_features_dim, self.metadata_augment_dim)
    
    self.attend_img = nn.Linear(self.metadata_augment_dim,self.img_extractor_dim)
    self.attend_metadata = nn.Linear(self.img_extractor_dim,self.metadata_augment_dim)
    
    self.linearlayer_concat = LinearLayerBlock(self.img_extractor_dim + self.metadata_augment_dim, 64)

    self.classifier = nn.Linear(64,classifier_dims)

  def forward(self,metadata,imgs):
    
    img_features = self.pretrained_model(imgs).reshape(-1,self.out_conv_dim)
    img_features = self.img_extractor_norm(self.img_extractor(img_features))
    
    metadata_features = self.metadata_augment(metadata)
    
    transformed_metadata = self.attend_img(metadata_features)
    img_with_metadata = img_features * transformed_metadata
    
    transformed_img = self.attend_metadata(img_features)
    metadata_with_img = metadata_features * transformed_img
    
    conc_features = torch.cat((img_with_metadata,metadata_with_img), dim = 1)
    conc_features = self.linearlayer_concat(conc_features)

    output_features = self.classifier(conc_features)

    return output_features

def get_MobileNet(classifier_dims, print_flag = False):
    
    model = mobilenet_v3_large(weights = 'DEFAULT')
    input_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(input_features, out_features = classifier_dims, bias = True)
    
    if print_flag:
        print("Architecture of MobileNet v3 Large:")
        print(model)
    
    return model
    
def get_GoogleNet(classifier_dims, print_flag = False):
    
    model = googlenet(weights = 'DEFAULT')

    input_features = model.fc.in_features
    model.fc = nn.Linear(in_features = input_features, out_features = classifier_dims, bias = True)
    
    if print_flag:
        print("Architecture of GoogleNet:")
        print(model)
    
    return model

def get_EfficientNet(classifier_dims, print_flag = False):
    
    model = efficientnet_v2_s(weights = 'DEFAULT')

    input_features = model.classifier[1].in_features
    model.classifier[1] = nn.Linear(input_features, out_features = classifier_dims, bias = True)
    
    if print_flag:
        print("Architecture of Efficient Net v2 Small:")
        print(model)
    
    return model

def get_Resnet(classifier_dims, print_flag = False):
    
    model = resnet34(weights = 'DEFAULT')

    input_features = model.fc.in_features
    model.fc = nn.Linear(input_features, out_features = classifier_dims, bias = True)
    
    if print_flag:
        print("Architecture of Resnet34:")
        print(model)
    
    return model

def get_MobileNetSa(classifier_dims, print_flag = False):
    
    model = mobilenet_v3_large(weights = 'DEFAULT')
    input_features = model.classifier[3].in_features
    model.classifier[3] = nn.Linear(input_features, out_features = classifier_dims, bias = True)
    
    model.features[10].block.add_module("AttentionBlock_10", AttentionBlock(80))
    model.features[11].block.add_module("AttentionBlock_11", AttentionBlock(112))
    model.features[12].block.add_module("AttentionBlock_12", AttentionBlock(112))
    model.features[13].block.add_module("AttentionBlock_13", AttentionBlock(160))
    model.features[14].block.add_module("AttentionBlock_14", AttentionBlock(160))
    model.features[15].block.add_module("AttentionBlock_15", AttentionBlock(160))
    
    if print_flag:
        print("Architecture of MobileNet v3 Large + SA:")
        print(model)
    
    return model
    
def get_metaModel_Resnet(pretrained_model,metadata_features_dim ,classifier_dims, metaModelType = 1):
    
    pretrained_model.fc = Identity()
    if metaModelType:
        metaModel_Resnet = MetadataClassifierLinear(pretrained_model, 512, metadata_features_dim, classifier_dims)
    else:
        metaModel_Resnet = MetadataClassifierLinearMultiplication(pretrained_model, 512, metadata_features_dim, classifier_dims)
        
    return metaModel_Resnet

def get_metaModel_GoogleNet(pretrained_model,metadata_features_dim ,classifier_dims, metaModelType = 1):
    
    pretrained_model.fc = Identity()
    if metaModelType:
        metaModel_GoogleNet = MetadataClassifierLinear(pretrained_model, 1024, metadata_features_dim, classifier_dims)
    else:
        metaModel_GoogleNet = MetadataClassifierLinearMultiplication(pretrained_model, 1024, metadata_features_dim, classifier_dims)
    
    return metaModel_GoogleNet
    
def get_metaModel_EfficientNet(pretrained_model,metadata_features_dim ,classifier_dims, metaModelType = 1):
    
    pretrained_model.classifier = Identity()
    if metaModelType:
        metaModel_EfficientNet = MetadataClassifierLinear(pretrained_model,1280,metadata_features_dim ,classifier_dims)
    else:
        metaModel_EfficientNet = MetadataClassifierLinearMultiplication(pretrained_model,1280,metadata_features_dim ,classifier_dims)
        
    return metaModel_EfficientNet

def get_metaModel_MobileNets(pretrained_model,metadata_features_dim ,classifier_dims, metaModelType = 1):
    
    pretrained_model.classifier = Identity()
    if metaModelType:
        metaModel_MobileNet = MetadataClassifierLinear(pretrained_model,960,metadata_features_dim ,classifier_dims)
    else:
        metaModel_MobileNet = MetadataClassifierLinearMultiplication(pretrained_model,960,metadata_features_dim ,classifier_dims)
        
    return metaModel_MobileNet