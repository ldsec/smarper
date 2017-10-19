function [post nlZ dnlZ] = infKL_m_init(hyp, mean, cov, lik, x, y)
lik_name = func2str(lik{1});

snu2=hyp.snu2;
% GP prior
n = size(x,1);
assert(size(y,1)==n)
C=max(y);
K = feval(cov{:}, hyp.cov, x);                  % evaluate the covariance matrix
m = feval(mean{:}, hyp.mean, x);                      % evaluate the mean vector
K=snu2*eye(n)+K;

K_C=zeros(C*n,C*n);
for j=1:C
	K_C((j-1)*n+1:j*n,(j-1)*n+1:j*n)=K;
end

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%init value
m_u=hyp.init_m;%k=0
V_u=hyp.init_V;
assert(size(m_u,1)==C*n);
assert(size(V_u,1)==C*n);
assert(size(V_u,2)==C*n);
tW = zeros(C,C,n);%k=-1
for i=1:n
	tW(:,:,i)=eye(C);
end
%tW = ones(C,C,n);%k=-1
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

if hyp.stochastic_approx==1
%df C-by-n
%dv C-by-C-by-n
[df, dv] = sampling_m_E(y, m_u, V_u, lik_name, 'random', hyp.sample_size);
else
  error('do not support')
end

tW = -2*dv;
tW_C=convert_C(tW);
[L D]=ldl(tW_C);
%sd=sqrt(abs(diag(D))).*sign(diag(D));
sd=sqrt(abs(diag(D)));
L=L*diag(sd);
R=chol(L'*K_C*L + eye(n*C));%R'*R==L'*K_C*L + diag(inv_d)
%use this following line if we approximate r^{k} .* tW.^{k-1} by tW.{k}
T = R'\(L'*K_C);

alpha=zeros(C*n,1);
for j=1:C
	idx=(j-1)*n+1:j*n;
	alpha(idx,:)=K\(m_u(idx,:)-m);
end
post.sW = R;                                             % return argument
post.alpha = alpha;
post.L = L;                                              % L'*L=B=eye(n)+sW*K*sW
nlZ=batch_nlz_m_full(lik_name, hyp, K, m, m_u, V_u, y, R,L,T);
fprintf('final: %.4f\n', nlZ);

if nargout>2
  warning('to be implemented\n');
  dnlZ = NaN;
end

function tW_C = convert_C(tW)
	C=size(tW,1);
	n=size(tW,3);
	C_n=C*n;
	tW_C=zeros(C_n,C_n);
	for i=1:n
	  idx=i:n:C_n;
	  tW_C(idx,idx)=tW(:,:,i);
	end
