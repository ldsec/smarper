function [post nlZ dnlZ] = infKL_m_prox(hyp, mean, cov, lik, x, y)
lik_name = func2str(lik{1});

if hyp.is_cached==1
	global cache_post;
	global cache_nlz;
	global cache_idx;
	
	post=cache_post(cache_idx);
	nlZ=cache_nlz(cache_idx);
	if nargout>2
		warning('to be implemented\n');
		dnlZ = NaN;
	end
	return 
end

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

kmax = hyp.max_pass;                                      % maximum number of iterations
rate=hyp.learning_rate;

% iterate
for k = 1:kmax                                                         % iterate
  % step size
  beta = rate;
  r = 1/(beta+1);
  
  if hyp.stochastic_approx==1
	%df C-by-n
	%dv C-by-C-by-n
    [df, dv] = sampling_m_E(y, m_u, V_u, lik_name, 'random', hyp.sample_size);
  else
	  error('do not support')
  end

  pseudo_y=zeros(C*n,1);
  for j=1:C
	  idx=(j-1)*n+1:j*n;
	  % pseudo observation
	  pseudo_y(idx,:) = m + K*(df(j,:)') - m_u(idx,:);
  end

  %remove the following if-else statement if we approximate r^{k} .* tW.^{k-1} by tW.{k}
  if isfield(hyp,'exact')
	  if k==1
		  m_u=m_u+(1-r).*pseudo_y;%m^{1}
	  else
		  tW_C=r .* convert_C(tW);%r^k .* tW^{k-1}
		  [L D]=ldl(tW_C);
		  sd=sqrt(abs(diag(D)));
		  L=L*diag(sd);
		  R=chol(L'*K_C*L + eye(n*C));
		  m_u = m_u + (1-r).*(pseudo_y - K_C*( L*(R\(R'\(L'*pseudo_y))) )  );%m^{k+1}
	  end
  end
  W = -2*dv;
  tW = r.*tW + (1-r).*W;%tW^{k}
  tW_C=convert_C(tW);
  [L D]=ldl(tW_C);
  %sd=sqrt(abs(diag(D))).*sign(diag(D));
  sd=sqrt(abs(diag(D)));
  L=L*diag(sd);

  R=chol(L'*K_C*L + eye(n*C));%R'*R==L'*K_C*L + diag(inv_d)
  %use this following line if we approximate r^{k} .* tW.^{k-1} by tW.{k}
  if ~isfield(hyp,'exact')
	  m_u = m_u + (1-r).*(pseudo_y - K_C*( L*(R\(R'\(L'*pseudo_y))) )  );%m^{k+1}
  end

  T = R'\(L'*K_C);
  V_u=K_C-T'*T;
  chol(V_u);

  nlZ_iter=batch_nlz_m_full(lik_name, hyp, K, m, m_u, V_u, y, R,L,T);
  fprintf('pass:%d) %.4f\n', k, nlZ_iter);

  if hyp.is_save==1
	global cache_post;
	global cache_nlz;

	alpha=zeros(C*n,1);
	for j=1:C
		idx=(j-1)*n+1:j*n;
		alpha(idx,:)=K\(m_u(idx,:)-m);
    end

	post.sW = R;                                             % return argument
	post.alpha = alpha;
	post.L = L;                                              % L'*L=B=eye(n)+sW*K*sW

	cache_post=[cache_post; post];
	cache_nlz=[cache_nlz; nlZ_iter];
  end

end

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
